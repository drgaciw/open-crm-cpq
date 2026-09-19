/**
 * API helper for the UAT: fixture seeding, persistence verification and teardown.
 *
 * Goes through the same origin as the browser (dev server -> gateway), using Playwright's
 * APIRequestContext (`page.request`), so it sees exactly what the app sees — including the
 * gateway's Redis rate limiter (50/s, burst 100). A serial browser never trips it, but a
 * teardown loop can, so 429 is retried with Retry-After awareness rather than reported.
 */
import { BASE } from './browser.mjs';

export class ApiCallError extends Error {
  constructor(method, url, status, body) {
    super(`${method} ${url} -> HTTP ${status}${body ? `: ${String(body).slice(0, 200)}` : ''}`);
    this.name = 'ApiCallError';
    this.status = status;
    this.body = body;
  }
}

/**
 * Strip the backend's ApiResponse<T> envelope. Page results stay wrapped (`{content, …}`)
 * because callers need the paging metadata; non-envelope bodies pass through.
 */
export function unwrap(body) {
  if (body && typeof body === 'object' && !Array.isArray(body) && 'success' in body && 'data' in body) {
    return body.data;
  }
  return body;
}

const defaultSleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * @param {import('playwright').APIRequestContext} request
 * @param {object} [options]
 * @param {string} [options.base]
 * @param {number} [options.maxRetries]
 * @param {(ms:number)=>Promise<void>} [options.sleep] Injected for tests.
 */
export function createApi(request, { base = BASE, maxRetries = 4, sleep = defaultSleep } = {}) {
  /** @type {{type:string,id:string|number,path:string}[]} */
  const ledger = [];

  async function call(method, path, data) {
    const url = `${base}${path}`;
    const fn = { GET: 'get', POST: 'post', PUT: 'put', DELETE: 'delete' }[method];
    for (let attempt = 0; ; attempt++) {
      const response = await request[fn](url, data === undefined ? {} : { data });
      const status = response.status();

      if (status === 429 && attempt < maxRetries) {
        const retryAfter = Number(response.headers()['retry-after']);
        await sleep(Number.isFinite(retryAfter) && retryAfter >= 0 ? retryAfter * 1000 : 2 ** attempt * 250);
        continue;
      }
      if (!response.ok()) {
        throw new ApiCallError(method, url, status, await response.text().catch(() => ''));
      }
      if (status === 204) return null;
      const text = await response.text();
      if (!text) return null;
      try {
        return unwrap(JSON.parse(text));
      } catch {
        return text;
      }
    }
  }

  return {
    get: (path) => call('GET', path),
    post: (path, data = {}) => call('POST', path, data),
    put: (path, data = {}) => call('PUT', path, data),
    del: (path) => call('DELETE', path),

    /** Record something this run created so teardown can remove it. */
    track(type, id, path) {
      ledger.push({ type, id, path });
    },
    /** Forget a record the test already deleted through the UI. */
    untrack(type, id) {
      const i = ledger.findIndex((e) => e.type === type && String(e.id) === String(id));
      if (i >= 0) ledger.splice(i, 1);
    },
    ledger,

    /**
     * Delete everything tracked, newest first (dependents were created after their parents).
     * Never throws: what could not be removed is returned as residue for the report.
     * A 404 means it is already gone, which is success.
     */
    async teardown() {
      const residue = [];
      for (const entry of [...ledger].reverse()) {
        try {
          await call('DELETE', entry.path);
        } catch (error) {
          if (error.status !== 404) residue.push({ ...entry, reason: error.message });
        }
      }
      ledger.length = 0;
      return residue;
    },
  };
}
