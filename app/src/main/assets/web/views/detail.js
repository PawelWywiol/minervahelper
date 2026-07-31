import * as api from '../api.js';
import { el, size, spinner } from '../dom.js';

/**
 * One ROM, in full: what the archive knows, what the cover lookup found, and where the file would
 * land. Opened from any list and closed back into the list that opened it — the browser's own Back
 * button is not involved, because the lists keep scroll and paging state the hash cannot carry.
 */
export function detail(mount, { onError }) {
  const fact = (key, value) =>
    el('div', { className: 'fact' }, [
      el('span', { className: 'k' }, key),
      el('span', { className: 'v' }, value),
    ]);

  async function open(rom, back) {
    mount.replaceChildren(spinner(`Loading ${rom.name}…`));
    let full;
    try {
      full = await api.rom(rom.id);
    } catch (error) {
      onError(error, () => open(rom, back));
      return;
    }

    const queueButton = el('button', { type: 'button' });
    const favouriteButton = el('button', { type: 'button', className: 'ghost' });
    const paintButtons = (marks) => {
      queueButton.textContent = marks.queued ? 'Remove from queue'
        : marks.downloaded ? 'Download again' : 'Download';
      favouriteButton.textContent = marks.favourite ? 'Remove from favourites' : 'Add to favourites';
    };
    paintButtons(full);

    const act = async (request) => {
      queueButton.disabled = favouriteButton.disabled = true;
      try {
        Object.assign(full, await request());
        paintButtons(full);
      } catch (error) {
        onError(error, () => open(rom, back));
      } finally {
        queueButton.disabled = favouriteButton.disabled = false;
      }
    };
    queueButton.addEventListener('click', () => act(() => api.toggleQueue(full.id)));
    favouriteButton.addEventListener('click', () => act(() => api.toggleFavourite(full.id)));

    const backButton = el('button', { type: 'button', className: 'link' }, '‹ Back');
    backButton.addEventListener('click', back);

    mount.replaceChildren(
      el('nav', { className: 'crumbs' }, backButton),
      el('div', { className: 'detail' }, [
        full.cover ? el('img', { className: 'cover', src: full.cover, alt: `Cover of ${full.name}` }) : null,
        el('div', {}, [
          el('h1', {}, full.title || full.name),
          full.title ? el('p', { className: 'muted' }, full.name) : null,
          el('div', { className: 'facts-rows' }, [
            fact('System', full.system || full.platform),
            fact('Collection', full.collection),
            fact('Size', size(full.size)),
            fact('Goes to', full.folder),
            fact('Torrent', full.torrentPath.split('/').pop()),
            fact('MD5', full.md5 || '—'),
            fact('SHA1', full.sha1 || '—'),
          ]),
          el('div', { className: 'actions' }, [queueButton, favouriteButton]),
        ].filter(Boolean)),
      ].filter(Boolean)),
    );
  }

  return { open };
}
