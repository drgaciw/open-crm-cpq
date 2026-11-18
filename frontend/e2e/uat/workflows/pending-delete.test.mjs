import test from 'node:test';
import assert from 'node:assert/strict';
import { pendingDeleteRefusal, matchesDelete } from './pending-delete.mjs';
const base = 'http://localhost:4200';
const message = 'Cannot delete a quote while approval is pending';
function fixture(options = {}) {
  const steps = [];
  let resolveResponse;
  const response = { url: () => `${base}/api/v1/quotes/7`, request: () => ({ method: () => 'DELETE' }),
    status: () => options.status ?? 409, json: async () => ({ errorCode: options.code ?? 'QUOTE_APPROVAL_PENDING', message }) };
  function page(actor) {
    let url = `${base}/`;
    const context = {};
    function locator(role, name) {
      return {
        first() { return this; }, filter() { return this; }, locator() { return this; },
        getByRole: (childRole, spec) => locator(childRole, spec?.name),
        async waitFor() { if (options.noBanner && role === 'alert') throw new Error('banner absent'); },
        async innerText() { return role === 'dialog' ? 'Delete quote UAT-test-quote' : `${message} [QUOTE_APPROVAL_PENDING]`; },
        async click() {
          steps.push(`${actor}:${role}:${name}`);
          if (actor === 'seller' && role === 'button' && name === 'Delete') {
            assert.ok(resolveResponse, 'response listener must be installed before confirming');
            if (options.navigateAway) url = `${base}/quotes`;
            resolveResponse(response);
          }
          if (actor === 'approver' && role === 'link') url = `${base}/quotes/7`;
        },
      };
    }
    return { context: () => context, url: () => url, getByRole: (role, spec) => locator(role, spec?.name), locator: () => locator('locator'),
      async goto(target) { url = target; steps.push(`${actor}:navigate:${target}`); }, async waitForLoadState() {},
      async waitForURL(predicate) { assert.ok(predicate(new URL(url))); },
      waitForResponse(predicate) {
        assert.ok(predicate(response)); steps.push('listen');
        return new Promise((resolve) => { resolveResponse = resolve; });
      },
    };
  }
  const original = { id: 91, quoteId: 7, status: 'PENDING', requestedBy: 'seller-id' };
  let quoteReads = 0;
  let requestReads = 0;
  const ctx = { base, isolatedFixtures: true, runId: 'test', sellerActor: 'seller-id', approverActor: 'approver-id', page: page('seller'),
    preflight: Object.fromEntries(['quote', 'product', 'pricing', 'pricebook', 'approvalsBff'].map((key) => [key, { state: 'up' }])),
    api: {
      async post(path, body) { assert.deepEqual(body, { tier: 'DEAL_DESK', expectedVersion: 4 }); steps.push('submit'); return { ...original, requestedBy: options.requestedBy ?? 'seller-id' }; },
      async get() { return { id: 7, status: 'PENDING_APPROVAL', version: 4, deleted: ++quoteReads > 2 && !!options.quoteChanged }; },
      async del() { throw new Error('API delete must never replace the browser action'); },
    },
    approver: { page: page('approver'), api: { async get() {
      requestReads++;
      if (options.missingRequest) return [];
      return [{ ...original, status: requestReads > 1 && options.requestChanged ? 'APPROVED' : 'PENDING' }];
    } } },
  };
  return { ctx, steps, seed: async () => { steps.push('seed'); return { quote: { id: 7, name: 'UAT-test-quote' } }; } };
}

test('confirms seller UI Delete, correlates 409 and opens same request in separate approver context', async () => {
  const { ctx, steps, seed } = fixture();
  assert.match(await pendingDeleteRefusal(ctx, { seed }), /canonical request 91 unchanged/);
  assert.ok(steps.indexOf('seller:menuitem:Delete') < steps.indexOf('listen'));
  assert.ok(steps.indexOf('listen') < steps.indexOf('seller:button:Delete'));
  assert.ok(steps.indexOf('seller:button:Delete') < steps.indexOf('approver:link:Quote 7'));
  assert.equal(new URL(ctx.page.url()).pathname, '/quotes/7');
});
for (const [label, options, pattern] of [
  ['successful deletion', { status: 204 }, /204 !== 409/],
  ['wrong conflict code', { code: 'OTHER_CONFLICT' }, /QUOTE_APPROVAL_PENDING/],
  ['quote mutation', { quoteChanged: true }, /quote field/],
  ['approval decision', { requestChanged: true }, /unchanged and pending/],
  ['missing banner', { noBanner: true }, /banner absent/],
  ['seller navigated away', { navigateAway: true }, /quotes/],
  ['missing canonical request', { missingRequest: true }, /canonical request/],
  ['wrong seller session', { requestedBy: 'another-seller' }, /synthetic seller/],
]) test(`does not pass on ${label}`, async () => {
  const { ctx, seed } = fixture(options);
  await assert.rejects(pendingDeleteRefusal(ctx, { seed }), pattern);
});

test('blocks non-isolated or single-session runs before seeding', async () => {
  for (const change of [
    (ctx) => { ctx.isolatedFixtures = false; },
    (ctx) => { ctx.base = 'https://production.example'; },
    (ctx) => { ctx.approver.page = ctx.page; },
    (ctx) => { ctx.approverActor = ctx.sellerActor; },
  ]) {
    const { ctx, seed, steps } = fixture(); change(ctx);
    await assert.rejects(pendingDeleteRefusal(ctx, { seed }), (e) => e.kind === 'blocked');
    assert.deepEqual(steps, []);
  }
});

test('response matcher rejects unrelated method, quote and origin', () => {
  for (const [url, method, expected] of [
    [`${base}/api/v1/quotes/7`, 'DELETE', true], [`${base}/api/v1/quotes/7`, 'GET', false],
    [`${base}/api/v1/quotes/70`, 'DELETE', false], ['https://other.example/api/v1/quotes/7', 'DELETE', false],
  ]) assert.equal(matchesDelete({ url: () => url, request: () => ({ method: () => method }) }, 7, base), expected);
});
