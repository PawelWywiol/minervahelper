import * as api from '../api.js';
import { el, spinner } from '../dom.js';

/**
 * The settings a browser is allowed to change. The destination root is shown but not editable: it
 * is granted through Android's own folder picker, which no web page can open.
 */
export function settings(mount, { onError }) {
  let editing = null;

  const apply = async (change) => {
    try {
      paint(await api.updateSettings(change));
    } catch (error) {
      onError(error, show);
    }
  };

  function concurrency(data) {
    const options = [];
    for (let n = data.minConcurrentDownloads; n <= data.maxConcurrentDownloads; n++) options.push(n);
    return el('div', { className: 'actions' }, options.map((n) => {
      const button = el('button', {
        type: 'button',
        className: n === data.concurrentDownloads ? '' : 'ghost',
      }, String(n));
      button.addEventListener('click', () => apply({ concurrentDownloads: n }));
      return button;
    }));
  }

  function editor(platform) {
    const input = el('input', { type: 'text', value: platform.folder, id: 'folder' });
    const move = el('input', { type: 'checkbox', id: 'move', checked: true });
    const save = el('button', { type: 'button' }, 'Save');
    const cancel = el('button', { type: 'button', className: 'ghost' }, 'Cancel');
    save.addEventListener('click', () => {
      const folder = input.value.trim();
      if (!folder) return;
      editing = null;
      apply({
        collection: platform.collection,
        platformKey: platform.platformKey,
        folder,
        moveFiles: move.checked,
      });
    });
    cancel.addEventListener('click', () => { editing = null; show(); });

    return el('div', { className: 'editor' }, [
      el('label', { htmlFor: 'folder' }, `Folder for ${platform.platformKey}`),
      input,
      platform.downloaded > 0
        ? el('label', { htmlFor: 'move', className: 'checkline' }, [
          move,
          ` Move the ${platform.downloaded} file(s) already downloaded into the new folder`,
        ])
        : null,
      el('div', { className: 'actions' }, [save, cancel]),
    ].filter(Boolean));
  }

  function paint(data) {
    const rows = data.platforms.map((platform) => {
      if (editing === platform.platformKey) return editor(platform);
      const change = el('button', { type: 'button', className: 'ghost small' }, 'Change');
      change.addEventListener('click', () => { editing = platform.platformKey; paint(data); });
      return el('div', { className: 'row static' }, [
        el('span', { className: 'name' }, platform.platformKey),
        el('span', { className: 'meta' },
          `${platform.folder} · ${platform.queued + platform.downloaded} ROMs` +
          (platform.mapped ? '' : ' · no standard folder')),
        el('span', { className: 'buttons' }, change),
      ]);
    });

    mount.replaceChildren(
      el('h1', {}, 'Settings'),
      el('p', { className: data.root ? 'muted' : 'warn' }, data.root
        ? `Downloads land under ${data.root}. The folder itself can only be changed on the device.`
        : 'No destination folder chosen — open the app on the device and pick one.'),
      el('h2', {}, 'Downloads at once'),
      el('p', { className: 'muted' }, 'More is not always faster — each torrent has to find its own sharers.'),
      concurrency(data),
      el('h2', {}, 'Where each platform lands'),
      rows.length === 0
        ? el('p', { className: 'muted' }, 'Queue something to see where it will go.')
        : el('div', { className: 'list' }, rows),
    );
  }

  async function show() {
    mount.replaceChildren(spinner('Loading settings…'));
    try {
      paint(await api.settings());
    } catch (error) {
      onError(error, show);
    }
  }

  return {
    open() {
      editing = null;
      show();
    },
  };
}
