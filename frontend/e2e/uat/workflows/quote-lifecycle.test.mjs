import test from 'node:test';
import assert from 'node:assert/strict';
import { draftWithLine } from './quote-lifecycle.mjs';

test('quote pricing fixtures use isolated customer and regional price book and track entry cleanup', async () => {
  const calls = [];
  const ledger = [];
  let id = 0;
  const api = {
    async post(path, body) { calls.push({ path, body }); return { id: ++id, ...body }; },
    async get() { throw new Error('fixtures must not search or mutate shared price books'); },
    track(type, id, path) { ledger.push({ type, id, path }); },
  };
  const ctx = { api, runId: 'ISOLATED1' };
  const { quote } = await draftWithLine(ctx);
  const customer = calls.find((x) => x.path === '/api/v1/pricing-customers');
  const book = calls.find((x) => x.path === '/api/v1/price-books');
  const entry = calls.find((x) => /price-books\/\d+\/entries$/.test(x.path));
  assert.equal(book.body.isDefault, false);
  assert.equal(quote.region, book.body.region);
  assert.equal(quote.customerExternalId, customer.body.externalId);
  assert.notEqual(customer.body.externalId, 'CUST-GOLD-001');
  assert.ok(entry);
  assert.ok(ledger.some((x) => x.path === `${entry.path}/${calls.indexOf(entry) + 1}`));
});

test('quote fixture preserves explicitly requested region', async () => {
  let id = 0;
  const api = { post: async (_path, body) => ({ id: ++id, ...body }), track() {} };
  const { quote } = await draftWithLine({ api, runId: 'ISOLATED2' }, { region: 'EMEA' });
  assert.equal(quote.region, 'EMEA');
});
