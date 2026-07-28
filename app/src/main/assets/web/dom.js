/** The whole view layer: build nodes, format numbers. No framework, because four screens do not need one. */

export function el(tag, props = {}, children = []) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === 'dataset') Object.assign(node.dataset, value);
    else node[key] = value;
  }
  for (const child of [].concat(children)) {
    if (child == null || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(child));
  }
  return node;
}

export const count = (value) => new Intl.NumberFormat().format(value);

/** Matches the app's own formatSize: decimal units, one decimal place. */
export function size(bytes) {
  if (bytes >= 1e9) return `${(bytes / 1e9).toFixed(1)} GB`;
  if (bytes >= 1e6) return `${(bytes / 1e6).toFixed(1)} MB`;
  if (bytes >= 1e3) return `${Math.floor(bytes / 1e3)} kB`;
  return `${bytes} B`;
}

/** A row that behaves like a button for both a pointer and a keyboard. */
export function row(onActivate, children, extra = {}) {
  const node = el('button', { type: 'button', className: 'row', ...extra }, children);
  node.addEventListener('click', onActivate);
  return node;
}

export const spinner = (label = 'Loading…') => el('p', { className: 'muted' }, label);

export function marker(rom) {
  if (rom.downloaded) return el('span', { className: 'tag done', title: 'Already downloaded' }, 'on device');
  if (rom.queued) return el('span', { className: 'tag queued', title: 'Waiting in the queue' }, 'queued');
  return el('span', { className: 'tag' }, '');
}

/**
 * A ROM row that queues on press. The row repaints from what the device answers, not from what we
 * assumed would happen: a file already sitting in the destination comes back as downloaded rather
 * than queued, and the marker has to say so.
 */
export function romRow(rom, meta, { queue, details, onError }) {
  let tag = marker(rom);
  const node = row(async () => {
    node.disabled = true;
    try {
      const next = marker({ ...rom, ...(await queue(rom.id)) });
      tag.replaceWith(next);
      tag = next;
    } catch (error) {
      onError(error);
    } finally {
      node.disabled = false;
    }
  }, [el('span', { className: 'name' }, rom.name), el('span', { className: 'meta' }, meta), tag]);
  node.title = 'Press to queue or unqueue on the device';

  const more = el('button', { type: 'button', className: 'ghost small', title: `Details of ${rom.name}` }, 'Details');
  more.addEventListener('click', () => details(rom));
  return el('div', { className: 'rowline' }, [node, more]);
}
