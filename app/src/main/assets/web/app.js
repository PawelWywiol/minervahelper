import { NotPaired, pair, state } from './api.js';

const app = document.getElementById('app');

const el = (tag, props = {}, children = []) => {
  const node = Object.assign(document.createElement(tag), props);
  for (const child of [].concat(children)) {
    node.append(child instanceof Node ? child : document.createTextNode(child));
  }
  return node;
};

const count = (value) => new Intl.NumberFormat().format(value);

function renderGate(message) {
  const input = el('input', {
    type: 'text',
    inputMode: 'numeric',
    autocomplete: 'one-time-code',
    maxLength: 6,
    pattern: '[0-9]{6}',
    id: 'pin',
    required: true,
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
      if (await pair(input.value.trim())) {
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

function renderHome(status) {
  const facts = el('ul', { className: 'facts' }, [
    fact('Games in catalogue', count(status.romCount)),
    fact('In the queue', count(status.queued)),
    fact('Downloaded', count(status.downloaded)),
    fact('Downloads at once', count(status.concurrentDownloads)),
  ]);

  const notes = [];
  if (!status.catalogReady) {
    notes.push(el('p', { className: 'warn' }, 'The catalogue is not built yet — open the app on the device and let it import.'));
  }
  if (!status.rootChosen) {
    notes.push(el('p', { className: 'warn' }, 'No destination folder chosen on the device, so nothing can be saved yet.'));
  }

  app.replaceChildren(
    el('section', { className: 'card' }, [
      el('h1', {}, 'Minerva Helper'),
      el('p', { className: 'muted' }, `Paired · app version ${status.version}`),
      facts,
      ...notes,
    ]),
  );
}

async function start() {
  try {
    renderHome(await state());
  } catch (error) {
    if (error instanceof NotPaired) renderGate();
    else app.replaceChildren(el('section', { className: 'card' }, [el('p', { className: 'error' }, error.message)]));
  }
}

start();
