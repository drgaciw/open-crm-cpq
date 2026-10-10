import test from 'node:test';
import assert from 'node:assert/strict';
import { createCsrfOptions } from './csrf.mjs';

const base = 'http://localhost:4200';
const target = `${base}/api/v1/organizations`;
const cookie = (overrides = {}) => ({ name: 'XSRF-TOKEN', value: 'genuine%20token', domain: 'localhost', path: '/', expires: -1, secure: false, httpOnly: false, ...overrides });
function context(cookies = [], { status = 204, issueCookie = true } = {}) {
  const gets = [];
  return {
    gets,
    storageState: async () => ({ cookies }),
    get: async (url, options) => {
      gets.push({ url, options });
      if (issueCookie) cookies.push(cookie());
      return { ok: () => status >= 200 && status < 300, status: () => status };
    },
  };
}

test('safe methods, other origins and non-API paths never read or expose the token', async () => {
  const request = { storageState: async () => { throw new Error('cookie access'); } };
  const optionsFor = createCsrfOptions(request, { base });
  const options = { headers: { Accept: 'application/json' } };
  for (const method of ['GET', 'HEAD', 'OPTIONS', 'TRACE']) assert.equal(await optionsFor(method, target, options), options);
  assert.equal(await optionsFor('POST', 'https://outside.example/api/v1/organizations', options), options);
  assert.equal(await optionsFor('POST', `${base}/login`, options), options);
});

test('expired, HttpOnly, secure-over-http and wrongly scoped cookies require bootstrap', async () => {
  const unusable = [
    { expires: Date.now() / 1000 - 1 }, { httpOnly: true }, { secure: true },
    { domain: 'notlocalhost' }, { domain: '.localhost.example' },
    { path: '/api/v1/organizations-other' }, { path: '/api/v1/organizations/child' },
  ];
  for (const invalid of unusable) {
    const request = context([cookie({ value: 'unusable', ...invalid })]);
    const optionsFor = createCsrfOptions(request, { base });
    const options = await optionsFor('POST', target, { timeout: 100, data: { name: 'synthetic' } });
    assert.equal(request.gets.length, 1, JSON.stringify(invalid));
    assert.equal(options.headers['X-XSRF-TOKEN'], 'genuine token');
    assert.equal(request.gets[0].options.timeout, 100);
    assert.equal(request.gets[0].options.maxRedirects, 0);
  }
});

test('valid domain cookies and the longest applicable path select the browser-scoped token', async () => {
  const request = context([cookie({ domain: '.localhost', value: 'root' }), cookie({ path: '/api/v1', value: 'specific' })]);
  const optionsFor = createCsrfOptions(request, { base });
  const options = await optionsFor('DELETE', target, { headers: { Accept: 'application/json' } });
  assert.equal(options.headers['X-XSRF-TOKEN'], 'specific');
  assert.equal(options.headers.Accept, 'application/json');
  assert.equal(request.gets.length, 0);
});

test('bootstrap with no usable cookie and malformed cookie encoding fail closed', async () => {
  const request = context([], { issueCookie: false });
  const optionsFor = createCsrfOptions(request, { base });
  await assert.rejects(() => optionsFor('POST', target), /did not issue a usable/);
  const malformed = context([cookie({ value: '%not-valid' })]);
  await assert.rejects(() => createCsrfOptions(malformed, { base })('POST', target), URIError);
  assert.equal(malformed.gets.length, 0);
});

test('concurrent first writes share one bootstrap and a failed bootstrap can be attempted anew', async () => {
  const request = context();
  const optionsFor = createCsrfOptions(request, { base });
  const results = await Promise.all([optionsFor('POST', target), optionsFor('PUT', target)]);
  assert.equal(request.gets.length, 1);
  assert.ok(results.every(options => options.headers['X-XSRF-TOKEN'] === 'genuine token'));
  const down = context([], { status: 503, issueCookie: false });
  const retryOptions = createCsrfOptions(down, { base });
  await assert.rejects(() => retryOptions('POST', target), /503/);
  await assert.rejects(() => retryOptions('POST', target), /503/);
  assert.equal(down.gets.length, 2);
});
