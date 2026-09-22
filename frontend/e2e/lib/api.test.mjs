/**
 * Unit tests for the API helper and preflight classifier. Run with: npm run e2e:test
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { createApi, unwrap, ApiCallError } from './api.mjs';
import { classify } from './preflight.mjs';

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
    get: (u, o) => next('GET', u, o),
    post: (u, o) => next('POST', u, o),
    put: (u, o) => next('PUT', u, o),
    delete: (u, o) => next('DELETE', u, o),
  };
}

const noSleep = async () => {};

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
