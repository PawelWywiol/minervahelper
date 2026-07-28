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
  return null;
}
