import * as api from './api.js';
import { count, el } from './dom.js';
import { browse } from './views/browse.js';
import { search } from './views/search.js';

const app = document.getElementById('app');

function gate(message) {
  const input = el('input', {
    type: 'text',
    inputMode: 'numeric',
    autocomplete: 'one-time-code',
    maxLength: 6,
    pattern: '[0-9]{6}',
    id: 'pin',
    required: true,
    className: 'pin',
  });
  const error = el('p', { className: 'error', role: 'alert' }, message || '');
  const submit = el('button', { type: 'submit' }, 'Pair this browser');
  const form = el('form', {}, [
    el('label', { htmlFor: 'pin' }, 'Six-digit code from the device'),
    input,
    submit,
    error,
  ]);

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    submit.disabled = true;
    error.textContent = '';
    try {
      if (await api.pair(input.value.trim())) {
        start();
        return;
      }
      error.textContent = 'That code did not work. Show a fresh one on the device.';
    } catch (failure) {
      error.textContent = failure.message;
    }
    input.value = '';
    submit.disabled = false;
    input.focus();
  });

  app.replaceChildren(
    el('section', { className: 'card gate' }, [
      el('h1', {}, 'Minerva Helper'),
      el('p', { className: 'muted' }, 'Open Settings on the device, turn the browser remote on, and show a pairing code.'),
      form,
    ]),
  );
  input.focus();
}

const fact = (key, value) =>
  el('li', {}, [el('div', { className: 'k' }, key), el('div', { className: 'v' }, value)]);

function overview(mount, status) {
  const notes = [];
  if (!status.catalogReady) {
    notes.push(el('p', { className: 'warn' }, 'The catalogue is not built yet — open the app on the device and let it import.'));
  }
  if (!status.rootChosen) {
    notes.push(el('p', { className: 'warn' }, 'No destination folder chosen on the device, so nothing can be saved yet.'));
  }
  mount.replaceChildren(
    el('h1', {}, 'Overview'),
    el('p', { className: 'muted' }, `Paired · app version ${status.version}`),
    el('ul', { className: 'facts' }, [
      fact('Games in catalogue', count(status.romCount)),
      fact('In the queue', count(status.queued)),
      fact('Downloaded', count(status.downloaded)),
      fact('Downloads at once', count(status.concurrentDownloads)),
    ]),
    ...notes,
  );
}

/**
 * One shell, three tabs, one mount point. The tab is kept in the URL hash so a reload — and the
 * browser's own Back button — land where the user was.
 */
function shell(status) {
  const mount = el('section', { className: 'card' });
  const onError = (error, retry) => {
    if (error instanceof api.NotPaired) {
      gate('The device unpaired this browser. Pair it again.');
      return;
    }
    const again = el('button', { type: 'button', className: 'ghost' }, 'Try again');
    again.addEventListener('click', retry);
    mount.replaceChildren(
      el('p', { className: 'error' },
        error instanceof api.NoCatalogue
          ? 'The device has no catalogue yet — import it in the app first.'
          : error.message),
      again,
    );
  };

  const views = {
    overview: { label: 'Overview', open: () => overview(mount, status) },
    browse: { label: 'Browse', ...browse(mount, { onError }) },
    search: { label: 'Search', ...search(mount, { onError }) },
  };

  const tabs = el('nav', { className: 'tabs', ariaLabel: 'Sections' });
  const paintTabs = (active) => {
    tabs.replaceChildren(...Object.entries(views).map(([key, view]) => {
      const button = el('button', {
        type: 'button',
        className: key === active ? 'tab on' : 'tab',
        ariaCurrent: key === active ? 'page' : 'false',
      }, view.label);
      button.addEventListener('click', () => { location.hash = key; });
      return button;
    }));
  };

  const show = () => {
    const key = location.hash.replace('#', '') || 'overview';
    const view = views[key] || views.overview;
    paintTabs(views[key] ? key : 'overview');
    view.open();
  };

  window.addEventListener('hashchange', show);
  app.replaceChildren(el('header', { className: 'bar' }, [
    el('span', { className: 'brand' }, 'Minerva Helper'),
    tabs,
  ]), mount);
  show();
}

async function start() {
  try {
    shell(await api.state());
  } catch (error) {
    if (error instanceof api.NotPaired) gate();
    else app.replaceChildren(el('section', { className: 'card' }, el('p', { className: 'error' }, error.message)));
  }
}

start();
