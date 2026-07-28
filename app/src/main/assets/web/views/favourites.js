import * as api from '../api.js';
import { count, el, romRow, size, spinner } from '../dom.js';

/** The same list the app keeps, over the same store — a heart tapped on the device shows up here. */
export function favourites(mount, { onError, onDetails }) {
  async function show() {
    mount.replaceChildren(spinner('Loading favourites…'));
    try {
      const { roms } = await api.favourites();
      mount.replaceChildren(
        el('h1', {}, 'Favourites'),
        el('p', { className: 'muted' }, roms.length === 0
          ? 'Nothing yet. Open a game and add it here — favourites survive a catalogue rebuild.'
          : `${count(roms.length)} saved`),
        el('div', { className: 'list' }, roms.map((rom) =>
          romRow(rom, `${size(rom.size)} · ${rom.platformKey}`, {
            queue: api.toggleQueue,
            details: (it) => onDetails(it, show),
            onError: (error) => onError(error, show),
          }))),
      );
    } catch (error) {
      onError(error, show);
    }
  }

  return { open: show };
}
