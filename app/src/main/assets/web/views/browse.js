import * as api from '../api.js';
import { count, el, marker, row, size, spinner } from '../dom.js';

/**
 * Collections → platforms → ROMs, the same three levels as the app.
 *
 * The ROM list keeps a window rather than everything: the largest platform holds 753k entries, and a
 * page that grows without limit eventually stops scrolling. Rows above the window are dropped and
 * reloaded on the way back up, exactly as the on-device pager does.
 */
const MAX_ROWS = 1000;

export function browse(mount, { onError }) {
  const render = (nodes) => mount.replaceChildren(...nodes);

  const crumbs = (...parts) =>
    el('nav', { className: 'crumbs', ariaLabel: 'Where you are' },
      parts.flatMap((part, index) => [
        index > 0 ? el('span', { className: 'sep', ariaHidden: 'true' }, '›') : null,
        part.onClick
          ? el('button', { type: 'button', className: 'link', onclick: part.onClick }, part.label)
          : el('span', { className: 'here' }, part.label),
      ]).filter(Boolean));

  async function showCollections() {
    render([spinner('Loading collections…')]);
    try {
      const { collections } = await api.collections();
      render([
        el('h1', {}, 'Browse'),
        el('p', { className: 'muted' }, `${collections.length} collections`),
        el('div', { className: 'list' }, collections.map((c) =>
          row(() => showPlatforms(c.name), [
            el('span', { className: 'name' }, c.name),
            el('span', { className: 'meta' }, `${count(c.fileCount)} files · ${c.platformCount} platforms`),
          ]))),
      ]);
    } catch (error) {
      onError(error, showCollections);
    }
  }

  async function showPlatforms(collection) {
    render([spinner('Loading platforms…')]);
    try {
      const { platforms } = await api.platforms(collection);
      render([
        crumbs({ label: 'Browse', onClick: showCollections }, { label: collection }),
        el('h1', {}, collection),
        el('div', { className: 'list' }, platforms.map((p) =>
          row(() => showRoms(collection, p), [
            el('span', { className: 'name' }, p.label),
            el('span', { className: 'meta' }, `${count(p.fileCount)} files · goes to ${p.folder}`),
          ]))),
      ]);
    } catch (error) {
      onError(error, () => showPlatforms(collection));
    }
  }

  async function showRoms(collection, platform) {
    const rows = [];
    let cursor = null;
    let more = true;
    let loading = false;

    const list = el('div', { className: 'list' });
    const status = el('p', { className: 'muted' }, 'Loading…');
    const moreButton = el('button', { type: 'button', className: 'ghost wide' }, 'Load more');

    // Plain rows, not buttons: nothing can be queued from the browser until W3 wires the queue up,
    // and a row that looks pressable but is not is worse than one that does not offer.
    const paint = () => {
      list.replaceChildren(...rows.map((rom) =>
        el('div', { className: 'row static' }, [
          el('span', { className: 'name' }, rom.name),
          el('span', { className: 'meta' }, size(rom.size)),
          marker(rom),
        ])));
      status.textContent = `${count(rows.length)} of ${count(platform.fileCount)} shown`;
      moreButton.hidden = !more;
    };

    async function loadMore() {
      if (loading || !more) return;
      loading = true;
      moreButton.disabled = true;
      try {
        const page = await api.roms(platform.id, cursor);
        rows.push(...page.roms);
        cursor = page.cursor;
        more = page.more;
        if (rows.length > MAX_ROWS) rows.splice(0, rows.length - MAX_ROWS);
        paint();
      } catch (error) {
        onError(error, () => showRoms(collection, platform));
      } finally {
        loading = false;
        moreButton.disabled = false;
      }
    }

    moreButton.addEventListener('click', loadMore);
    render([
      crumbs(
        { label: 'Browse', onClick: showCollections },
        { label: collection, onClick: () => showPlatforms(collection) },
        { label: platform.label },
      ),
      el('h1', {}, platform.label),
      status,
      list,
      moreButton,
    ]);
    await loadMore();
  }

  return { open: showCollections };
}
