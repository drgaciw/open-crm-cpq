/**
 * Unit tests for the API helper and preflight classifier. Run with: npm run e2e:test
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { createApi, unwrap, ApiCallError } from './api.mjs';
import { classify, runPreflight } from './preflight.mjs';

/** Minimal stand-in for Playwright's APIResponse. */
const res = (status, body, headers = {}) => ({
  status: () => status,
  ok: () => status >= 200 && status < 300,
  headers: () => headers,
  json: async () => body,
  text: async () => (typeof body === 'string' ? body : JSON.stringify(body)),
});

/** Fake APIRequestContext that replays a scripted sequence of responses. */
function fakeRequest(sequence) {
  const calls = [];
  const next = async (method, url, options) => {
    calls.push({ method, url, options });
    const item = sequence[Math.min(calls.length - 1, sequence.length - 1)];
    return item;
  };
  return {
    calls,
    storageState: async () => ({ cookies: [{ name: 'XSRF-TOKEN', value: 'test-token', domain: 'x', path: '/', expires: -1, secure: false, httpOnly: false }] }),
    get: (u, o) => next('GET', u, o),
    post: (u, o) => next('POST', u, o),
    put: (u, o) => next('PUT', u, o),
    delete: (u, o) => next('DELETE', u, o),
  };
}

const noSleep = async () => {};

const csrfCookie = (overrides = {}) => ({ name: 'XSRF-TOKEN', value: 'real%20token', domain: 'localhost', path: '/', expires: -1, secure: false, httpOnly: false, ...overrides });
function cookieRequest(cookies = [csrfCookie()], bootstrapStatus = 204) {
  const calls = [];
  return {
    calls,
    storageState: async () => ({ cookies }),
    get: async (url, options) => {
      calls.push({ method: 'GET', url, options });
      if (url.endsWith('/api/v1/csrf') && bootstrapStatus === 204) cookies.push(csrfCookie());
      return res(url.endsWith('/api/v1/csrf') ? bootstrapStatus : 200, {});
    },
    post: async (url, options) => { calls.push({ method: 'POST', url, options }); return res(201, { id: 1 }); },
    put: async (url, options) => { calls.push({ method: 'PUT', url, options }); return res(200, {}); },
    delete: async (url, options) => { calls.push({ method: 'DELETE', url, options }); return res(204, ''); },
  };
}

test('API writes echo a real scoped cookie and Origin, while reads send neither', async () => {
  const request = cookieRequest();
  const api = createApi(request, { base: 'http://localhost:4200' });
  await api.get('/api/v1/organizations');
  await api.post('/api/v1/organizations', { name: 'synthetic' });
  await api.put('/api/v1/organizations/1', { name: 'updated' });
  await api.del('/api/v1/organizations/1');
  assert.equal(request.calls[0].options.headers, undefined);
  for (const call of request.calls.slice(1)) {
    assert.equal(call.options.headers.Origin, 'http://localhost:4200');
    assert.equal(call.options.headers['X-XSRF-TOKEN'], 'real token');
    assert.equal(call.options.maxRedirects, 0);
  }
});

test('first API mutation bootstraps a genuine cookie before sending the mutation', async () => {
  const request = cookieRequest([]);
  const api = createApi(request, { base: 'http://localhost:4200' });
  await api.post('/api/v1/organizations', {});
  assert.deepEqual(request.calls.map(call => call.method), ['GET', 'POST']);
  assert.equal(request.calls[0].url, 'http://localhost:4200/api/v1/csrf');
  assert.equal(request.calls[1].options.headers['X-XSRF-TOKEN'], 'real token');
});

test('failed CSRF bootstrap prevents the mutation instead of retrying it', async () => {
  const request = cookieRequest([], 503);
  const api = createApi(request, { base: 'http://localhost:4200' });
  await assert.rejects(() => api.post('/api/v1/organizations'), /CSRF.*503/);
  assert.deepEqual(request.calls.map(call => call.method), ['GET']);
});

test('preflight POST probes use the same genuine token and Origin', async () => {
  const request = cookieRequest();
  const result = await runPreflight(request, { base: 'http://localhost:4200' });
  assert.equal(result.configValidate.state, 'up');
  for (const call of request.calls.filter(call => call.method === 'POST')) {
    assert.equal(call.options.headers.Origin, 'http://localhost:4200');
    assert.equal(call.options.headers['X-XSRF-TOKEN'], 'real token');
  }
});

// ── unwrap ────────────────────────────────────────────────────────────────────

test('unwrap returns data from an ApiResponse envelope', () => {
  assert.deepEqual(unwrap({ success: true, data: { id: 7 } }), { id: 7 });
});

test('unwrap keeps the page wrapper when the envelope carries pagination', () => {
  const body = { success: true, data: { content: [{ id: 1 }], totalElements: 1 } };
  assert.deepEqual(unwrap(body).content, [{ id: 1 }]);
});

test('unwrap passes through bodies that are not envelopes', () => {
  assert.deepEqual(unwrap([{ id: 1 }]), [{ id: 1 }]);
});

// ── retry / errors ────────────────────────────────────────────────────────────

test('retries a 429 honouring Retry-After, then succeeds', async () => {
  const request = fakeRequest([res(429, {}, { 'retry-after': '0' }), res(200, { success: true, data: { id: 1 } })]);
  const api = createApi(request, { base: 'http://x', sleep: noSleep });
  assert.deepEqual(await api.get('/api/v1/leads/1'), { id: 1 });
  assert.equal(request.calls.length, 2);
});

test('does not retry a 404 and throws ApiCallError with the status', async () => {
  const request = fakeRequest([res(404, { message: 'nope' })]);
  const api = createApi(request, { base: 'http://x', sleep: noSleep });
  await assert.rejects(() => api.get('/api/v1/leads/9'), (e) => e instanceof ApiCallError && e.status === 404);
  assert.equal(request.calls.length, 1);
});

test('gives up after maxRetries on persistent 429', async () => {
  const request = fakeRequest([res(429, {})]);
  const api = createApi(request, { base: 'http://x', sleep: noSleep, maxRetries: 2 });
  await assert.rejects(() => api.get('/x'), (e) => e.status === 429);
  assert.equal(request.calls.length, 3);
});

// ── ledger / teardown ─────────────────────────────────────────────────────────

test('teardown deletes ledgered records newest-first and reports what it could not remove', async () => {
  const request = fakeRequest([res(204, ''), res(409, { message: 'conflict' })]);
  const api = createApi(request, { base: 'http://x', sleep: noSleep });
  api.track('organization', 1, '/api/v1/organizations/1');
  api.track('quote', 2, '/api/v1/quotes/2');
  const residue = await api.teardown();
  // Newest first: the quote (depends on nothing later) goes before the organization.
  assert.deepEqual(request.calls.map((c) => c.url), ['http://x/api/v1/quotes/2', 'http://x/api/v1/organizations/1']);
  assert.equal(residue.length, 1);
  assert.equal(residue[0].type, 'organization');
});

// ── preflight classification ──────────────────────────────────────────────────

test('classifies backend probes', () => {
  assert.equal(classify({ status: 200 }), 'up');
  assert.equal(classify({ status: 204 }), 'up');
  assert.equal(classify({ status: 401 }), 'forbidden');
  assert.equal(classify({ status: 403 }), 'forbidden');
  assert.equal(classify({ status: 503 }), 'fallback');
  assert.equal(classify({ status: 500 }), 'down');
  assert.equal(classify({ status: 502 }), 'down');
  assert.equal(classify({ error: 'ECONNREFUSED' }), 'down');
});

test('a 4xx validation answer still proves the backend is up', () => {
  // POSTing an empty body to a validate endpoint yields 400/422 from a live service.
  assert.equal(classify({ status: 400 }), 'up');
  assert.equal(classify({ status: 422 }), 'up');
});
