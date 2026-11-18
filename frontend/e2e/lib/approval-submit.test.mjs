import test from 'node:test';
import assert from 'node:assert/strict';
import { approvalSubmitBody } from './approval-submit.mjs';

test('reads current version after fixture mutations and freezes command across retries', async () => {
  let version = 7;
  let reads = 0;
  const body = await approvalSubmitBody(async (path) => {
    assert.equal(path, '/api/v1/quotes/42'); reads++; return { version };
  }, 42, 'DEAL_DESK');
  version = 8; // concurrent edit must not silently create a new command identity
  assert.deepEqual(body, { tier: 'DEAL_DESK', expectedVersion: 7 });
  assert.equal(Object.isFrozen(body), true);
  assert.equal(reads, 1);
  assert.equal(JSON.stringify(body), JSON.stringify({ tier: 'DEAL_DESK', expectedVersion: 7 }));
});
for (const version of [undefined, null, -1, 1.5, '7', Number.MAX_SAFE_INTEGER + 1]) {
  test(`rejects invalid version ${version}`, async () => {
    await assert.rejects(approvalSubmitBody(async () => ({ version }), 42, 'CFO'), /current non-negative quote version/);
  });
}
test('accepts version zero', async () => {
  assert.deepEqual(await approvalSubmitBody(async () => ({ version: 0 }), 42, 'SALES_VP'),
    { tier: 'SALES_VP', expectedVersion: 0 });
});

test('existing API 429 retry reuses prepared version without rereading the quote', async () => {
  const { createApi } = await import('./api.mjs');
  const sent = [];
  let reads = 0;
  const response = (status, body) => ({ status: () => status, ok: () => status < 400,
    headers: () => ({ 'retry-after': '0' }), text: async () => JSON.stringify(body) });
  const api = createApi({
    storageState: async () => ({ cookies: [{ name: 'XSRF-TOKEN', value: 'test', domain: 'localhost', path: '/', expires: -1, secure: false, httpOnly: false }] }),
    get: async () => { reads++; return response(200, { version: 7 }); },
    post: async (_url, options) => { sent.push(options.data); return response(sent.length === 1 ? 429 : 201, { id: 91 }); },
  }, { base: 'http://localhost:4200', sleep: async () => {} });
  const body = await approvalSubmitBody((path) => api.get(path), 42, 'DEAL_DESK');
  assert.deepEqual(await api.post('/api/v1/quotes/42/approvals', body), { id: 91 });
  assert.equal(reads, 1);
  assert.equal(sent.length, 2);
  assert.strictEqual(sent[0], body);
  assert.strictEqual(sent[1], body);
});
