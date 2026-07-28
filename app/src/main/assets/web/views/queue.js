import * as api from '../api.js';
import { count, el, size } from '../dom.js';

/**
 * The queue, mirrored from the device once a second.
 *
 * Two rules shape this view. Polling stops the moment the tab is hidden or the user leaves the
 * view — a forgotten tab must not keep the handheld's CPU awake. And the list is only rebuilt when
 * its shape changes: a button that re-flows under a finger every second is how the wrong row gets
 * pressed, so the same nodes are kept and only their text is refreshed.
 */
const POLL_MS = 1000;

export function queue(mount, { onError }) {
  let timer = null;
  let shape = '';
  const updaters = new Map();

  const summary = el('p', { className: 'muted' });
  const free = el('p', { className: 'muted' });
  const hint = el('p', { className: 'muted' });
  const runButton = el('button', { type: 'button', className: 'ghost', hidden: true });
  const retryButton = el('button', { type: 'button', className: 'ghost', hidden: true }, 'Retry failed');
  const actions = el('div', { className: 'actions' }, [runButton, retryButton]);
  const groups = el('div');
  const history = el('div');

  let running = false;
  runButton.addEventListener('click', () => act(() => api.setRunning(!running)));
  retryButton.addEventListener('click', () => act(() => api.retryFailed()));

  async function act(request) {
    try {
      await request();
      await poll();
    } catch (error) {
      onError(error, poll);
    }
  }

  /** A destructive button arms itself first: nothing here is undoable by pressing again. */
  function confirmable(label, armedLabel, onConfirm) {
    const button = el('button', { type: 'button', className: 'ghost small' }, label);
    button.addEventListener('click', () => {
      if (button.dataset.armed) {
        onConfirm();
        return;
      }
      button.dataset.armed = '1';
      button.textContent = armedLabel;
      setTimeout(() => {
        delete button.dataset.armed;
        button.textContent = label;
      }, 4000);
    });
    return button;
  }

  function buildItem(item) {
    const meta = el('span', { className: 'meta' });
    const error = el('span', { className: 'error small' });
    const pause = el('button', { type: 'button', className: 'ghost small' });
    const remove = confirmable('Remove', 'Remove?', () => act(() => api.removeQueued(item.id)));
    pause.addEventListener('click', () =>
      act(() => api.pauseItem(item.id, pause.dataset.paused !== '1')));

    updaters.set(`item-${item.id}`, (fresh) => {
      meta.textContent = `${fresh.platform} · ${size(fresh.size)} · ${fresh.state} · → ${fresh.folder}`;
      error.textContent = fresh.error || '';
      error.hidden = !fresh.error;
      pause.dataset.paused = fresh.state === 'paused' ? '1' : '0';
      pause.textContent = fresh.state === 'paused' ? 'Resume' : 'Pause';
    });

    return el('div', { className: 'row static' }, [
      el('span', { className: 'name' }, [item.name, error]),
      meta,
      el('span', { className: 'buttons' }, [pause, remove]),
    ]);
  }

  function buildGroup(group, index, total) {
    const progress = el('p', { className: 'progress' });
    const fill = el('span');
    const bar = el('div', { className: 'meter' }, fill);
    const pause = el('button', { type: 'button', className: 'ghost small' });
    const up = el('button', { type: 'button', className: 'ghost small', disabled: index === 0 }, '↑');
    const down = el('button', { type: 'button', className: 'ghost small', disabled: index === total - 1 }, '↓');

    pause.addEventListener('click', () =>
      act(() => api.pauseGroup(group.torrentPath, pause.dataset.paused !== '1')));
    up.addEventListener('click', () => act(() => api.moveGroup(index, index - 1)));
    down.addEventListener('click', () => act(() => api.moveGroup(index, index + 1)));

    updaters.set(`group-${group.torrentPath}`, (fresh) => {
      pause.dataset.paused = fresh.paused ? '1' : '0';
      pause.textContent = fresh.paused ? 'Resume torrent' : 'Pause torrent';
      const active = fresh.active;
      progress.hidden = !active;
      bar.hidden = !active;
      if (active) {
        progress.textContent = `${active.percent}% · ${size(active.rate)}/s · ${active.peers} peers`;
        fill.style.width = `${active.percent}%`;
      }
    });

    return el('section', { className: 'group' }, [
      el('div', { className: 'group-head' }, [
        el('span', { className: 'name' }, group.label),
        el('span', { className: 'buttons' }, [up, down, pause]),
      ]),
      progress,
      bar,
      el('div', { className: 'list' }, group.items.map(buildItem)),
    ]);
  }

  function buildHistory(data) {
    if (data.downloadedTotal === 0) return [];
    const rows = data.downloaded.map((done) =>
      el('div', { className: 'row static' }, [
        el('span', { className: 'name' }, done.name),
        el('span', { className: 'meta' }, size(done.size) + (done.folder ? ` · in ${done.folder}` : '')),
        el('span', { className: 'buttons' }, confirmable('Forget', 'Forget?', () =>
          act(() => api.forgetDownloaded(done.id)))),
      ]));
    const rest = data.downloadedTotal - data.downloaded.length;
    return [
      el('h2', {}, 'Downloaded'),
      el('div', { className: 'list' }, rows),
      rest > 0 ? el('p', { className: 'muted' }, `…and ${count(rest)} more, all marked on device in the lists`) : null,
    ].filter(Boolean);
  }

  const shapeOf = (data) => [
    data.groups.map((g) => `${g.torrentPath}=${g.items.map((i) => i.id).join(',')}`).join('|'),
    data.downloaded.map((d) => d.id).join(','),
  ].join('#');

  function paint(data) {
    const next = shapeOf(data);
    if (next !== shape) {
      shape = next;
      updaters.clear();
      groups.replaceChildren(...data.groups.map((g, i) => buildGroup(g, i, data.groups.length)));
      history.replaceChildren(...buildHistory(data));
    }
    data.groups.forEach((group) => {
      updaters.get(`group-${group.torrentPath}`)?.(group);
      group.items.forEach((item) => updaters.get(`item-${item.id}`)?.(item));
    });

    running = data.working;
    summary.textContent =
      `${count(data.count)} ROMs · ${size(data.bytes)} · ${data.groups.length} torrents`;
    free.textContent = data.bytes > data.freeBytes
      ? `Will not fit — ${size(data.freeBytes)} free on the device`
      : `${size(data.freeBytes)} free on the device`;
    free.className = data.bytes > data.freeBytes ? 'warn' : 'muted';
    hint.textContent = !data.rootChosen
      ? 'No destination folder chosen on the device — open the app and pick one, or nothing can be saved.'
      : data.count === 0
        ? 'Nothing is waiting. Find a game in Browse or Search and press its row to queue it.'
        : data.working
          ? 'Downloading. Move a torrent up to have it fetched sooner.'
          : 'Paused — press Resume to continue. Progress made so far is kept.';
    hint.className = data.rootChosen ? 'muted' : 'warn';
    runButton.hidden = data.count === 0;
    runButton.textContent = data.working ? 'Pause all' : 'Resume';
    retryButton.hidden = !data.failed;
  }

  async function poll() {
    try {
      paint(await api.queue());
    } catch (error) {
      stop();
      onError(error, () => { start(); });
    }
  }

  const tick = async () => {
    if (document.hidden) return;
    await poll();
  };

  function start() {
    stop();
    timer = setInterval(tick, POLL_MS);
    document.addEventListener('visibilitychange', wake);
    poll();
  }

  function stop() {
    clearInterval(timer);
    timer = null;
    document.removeEventListener('visibilitychange', wake);
  }

  /** A tab brought back to the front should not show a second-old queue. */
  const wake = () => { if (!document.hidden) poll(); };

  return {
    open() {
      shape = '';
      updaters.clear();
      summary.textContent = 'Loading queue…';
      mount.replaceChildren(el('h1', {}, 'Queue'), summary, free, hint, actions, groups, history);
      start();
    },
    close: stop,
  };
}
