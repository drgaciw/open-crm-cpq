import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { requireDistinctGatewaySessions as check } from './pending-delete-session.mjs';
const options = { base: 'https://localhost:4200', now: 1000 };
const cookie = (value, changes = {}) => ({ name: 'OPENCRM_SESSION', value, domain: 'localhost',
  path: '/', secure: true, expires: -1, httpOnly: true, ...changes });
const state = (...cookies) => ({ cookies });
const seller = state(cookie('synthetic-seller'));
const approver = state(cookie('synthetic-approver'));

test('documented gateway default passes without override', () => {
  check(seller, approver, options);
  const properties = readFileSync(new URL('../../../backend/services/gateway-service/src/main/java/com/opencrm/gateway/security/GatewayBffProperties.java', import.meta.url), 'utf8');
  assert.match(properties, /cookieName = "OPENCRM_SESSION"/);
});
test('custom name including legacy SESSION only works when explicit', () => {
  for (const name of ['CUSTOM_TEST_SESSION', 'SESSION']) {
    const a = state(cookie('one', { name }));
    const b = state(cookie('two', { name }));
    assert.throws(() => check(a, b, options), /BLOCKED/);
    check(a, b, { ...options, cookieName: name });
  }
});
test('override is exact, valid and nonempty; no implicit default fallback', () => {
  for (const cookieName of ['', ' ', 'bad;name', 'other', 'opencrm_session']) {
    assert.throws(() => check(seller, approver, { ...options, cookieName }), /BLOCKED/);
  }
});
test('shared or empty values are refused', () => {
  assert.throws(() => check(seller, seller, options), /distinct/);
  for (const value of ['', ' ', undefined]) {
    assert.throws(() => check(seller, state(cookie(value)), options), /nonempty/);
  }
});
test('unrelated cookies cannot satisfy or replace selected credentials', () => {
  const unrelated = cookie('other', { name: 'XSRF-TOKEN' });
  check(state(unrelated, ...seller.cookies), state(unrelated, ...approver.cookies), options);
  assert.throws(() => check(state(unrelated), state(unrelated), options), /BLOCKED/);
});
test('host scope, root path, expiry and ambiguity are enforced', () => {
  for (const changes of [
    { domain: 'elsewhere.example' }, { domain: 'notlocalhost' }, { path: '/other' },
    { path: '/api' }, { expires: 999 }, { expires: 1000 }, { expires: undefined },
    { partitionKey: 'https://elsewhere.example' },
  ]) assert.throws(() => check(seller, state(cookie('two', changes)), options), /BLOCKED/);
  assert.throws(() => check(seller, state(cookie('two'), cookie('three')), options), /BLOCKED/);
  check(seller, state(cookie('two', { expires: 1001 })), options);
});
test('domain cookies require dot boundaries; host-only cookies do not cover subdomains', () => {
  const withDomain = (domain) => [state(cookie('one', { domain })), state(cookie('two', { domain }))];
  check(...withDomain('.example.test'), { ...options, base: 'https://app.example.test' });
  assert.throws(() => check(...withDomain('example.test'), { ...options, base: 'https://app.example.test' }), /BLOCKED/);
  assert.throws(() => check(...withDomain('.example.test'), { ...options, base: 'https://badexample.test' }), /BLOCKED/);
});
test('transport uses browser origin and supports documented localhost exception', () => {
  check(seller, approver, { ...options, base: 'http://localhost:4200' });
  const pair = (secure) => [state(cookie('one', { domain: '127.0.0.1', secure })), state(cookie('two', { domain: '127.0.0.1', secure }))];
  assert.throws(() => check(...pair(true), { ...options, base: 'http://127.0.0.1:4200' }), /BLOCKED/);
  check(...pair(false), { ...options, base: 'http://127.0.0.1:4200' });
});
test('diagnostics never include selected credential values', () => {
  const secret = 'synthetic-not-for-diagnostics';
  try { check(state(cookie(secret)), state(cookie(secret)), options); assert.fail('expected refusal'); }
  catch (error) { assert.ok(!error.message.includes(secret)); assert.match(error.message, /distinct/); }
});
