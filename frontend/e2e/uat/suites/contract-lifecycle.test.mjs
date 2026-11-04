import { test } from 'node:test';
import assert from 'node:assert/strict';
import suite from './contract-lifecycle.mjs';

async function observe(id, api) {
  const outcomes = [];
  const report = Object.fromEntries(['pass','fail','blocked','gap','inconclusive'].map(kind => [kind, (name, detail) => outcomes.push({kind,name,detail})]));
  const page = {locator: () => ({count: async () => 0}), url: () => 'http://test.invalid', keyboard:{press: async () => {}}, getByRole: () => ({count: async () => 0})};
  await suite.run({page, api, report, only:[id], runId:'unit'});
  assert.equal(outcomes.length,1);
  return outcomes[0];
}

test('subscription 403 is blocked, not service availability', async () => {
  const result = await observe('contract-lifecycle.api.subscriptions-up', {get: async () => {throw Object.assign(new Error('forbidden'), {status:403});}});
  assert.equal(result.kind,'blocked');
});

test('paginated subscription read can pass', async () => {
  const result = await observe('contract-lifecycle.api.subscriptions-up', {get: async () => ({content:[]})});
  assert.equal(result.kind,'pass');
});

test('generic gateway 410 does not establish deprecated renew behavior', async () => {
  const result = await observe('contract-lifecycle.api.renew-deprecated', {post: async () => {throw Object.assign(new Error('gone'), {status:410,body:'{"message":"deprecated"}'});}});
  assert.equal(result.kind,'blocked');
});

test('unexercised hold and null-start remain blocked', async () => {
  for (const id of ['contract-lifecycle.hold.armed','contract-lifecycle.null-start.unit-gate','contract-lifecycle.type-safety.contract-parity'])
    assert.equal((await observe(id,{})).kind,'blocked');
});
