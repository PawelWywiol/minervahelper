import * as api from '../api.js';
import { count, el, marker, size } from '../dom.js';

/** Long enough that a two-letter prefix does not sweep the whole 2.4M-row index on every keystroke. */
const DEBOUNCE_MS = 200;
const PAGE = 50;

export function search(mount, { onError }) {
  const input = el('input', {
    type: 'search',
    id: 'q',
    placeholder: 'Game name',
    autocomplete: 'off',
    spellcheck: false,
  });
  const status = el('p', { className: 'muted' }, 'Type to search the whole archive.');
  const list = el('div', { className: 'list' });
  const moreButton = el('button', { type: 'button', className: 'ghost wide', hidden: true }, 'Load more');

  let query = '';
  let offset = 0;
  let timer = null;
  let generation = 0;

  const paint = (roms, append) => {
    const nodes = roms.map((rom) =>
      el('div', { className: 'row static' }, [
        el('span', { className: 'name' }, rom.name),
        el('span', { className: 'meta' }, `${size(rom.size)} · ${rom.platformKey}`),
        marker(rom),
      ]));
    if (append) list.append(...nodes);
    else list.replaceChildren(...nodes);
  };

  async function run(append) {
    // Results arriving out of order would leave an older query's rows on screen; only the newest
    // request is allowed to paint.
    const mine = ++generation;
    const started = performance.now();
    try {
      const result = await api.search(query, offset);
      if (mine !== generation) return;
      paint(result.roms, append);
      const shown = list.childElementCount;
      status.textContent = shown === 0
        ? `Nothing matches “${query}”.`
        : `${count(shown)} shown · ${Math.round(performance.now() - started)} ms`;
      moreButton.hidden = !result.more;
    } catch (error) {
      if (mine === generation) onError(error, () => run(false));
    }
  }

  input.addEventListener('input', () => {
    clearTimeout(timer);
    query = input.value.trim();
    offset = 0;
    if (!query) {
      generation++;
      list.replaceChildren();
      moreButton.hidden = true;
      status.textContent = 'Type to search the whole archive.';
      return;
    }
    status.textContent = 'Searching…';
    timer = setTimeout(() => run(false), DEBOUNCE_MS);
  });

  moreButton.addEventListener('click', () => {
    offset += PAGE;
    run(true);
  });

  return {
    open() {
      mount.replaceChildren(
        el('h1', {}, 'Search'),
        el('label', { htmlFor: 'q' }, 'Search 2.4 million files'),
        input,
        status,
        list,
        moreButton,
      );
      input.focus();
    },
  };
}
