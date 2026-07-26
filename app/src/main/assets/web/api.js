const HEADER = 'X-Minerva';

/** Thrown when the browser is not paired; every screen treats it as "show the pairing gate". */
export class NotPaired extends Error {}

async function call(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { ...(options.headers || {}), [HEADER]: '1' },
    credentials: 'same-origin',
    cache: 'no-store',
  });
  if (response.status === 401) throw new NotPaired();
  if (!response.ok) throw new Error(`${options.method || 'GET'} ${path} → ${response.status}`);
  return response.status === 204 ? null : response.json();
}

export const get = (path) => call(path);

export const send = (path, body) =>
  call(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body ?? {}),
  });

export const state = () => get('/api/state');

/** Resolves false when the code was rejected — the reason is deliberately not disclosed. */
export async function pair(pin) {
  try {
    const result = await send('/api/pair', { pin });
    return Boolean(result && result.paired);
  } catch (error) {
    if (error instanceof NotPaired) return false;
    throw error;
  }
}
