/**
 * DRG-452 built-SPA browser acceptance with synthetic, in-memory HTTP fixtures.
 * Uses an ephemeral loopback port and installed headless Playwright Chromium.
 * Never forwards requests to a real API, touches port 4200, or starts an application service.
 * Run after `nx build crm`: node scripts/verification/quote-comparison-browser-acceptance.mjs
 * This proves browser binding/interaction only, not backend auth, persistence or PostgreSQL.
 */
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { resolve, extname, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { createHash } from 'node:crypto';

const root = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const built = resolve(root, 'frontend/dist/apps/crm/browser');
assert.ok(existsSync(resolve(built, 'index.html')), 'Build the CRM application first');
const require = createRequire(resolve(root, 'frontend/package.json'));
const { chromium } = require('playwright');
const artifacts = resolve(root, '.gitnexus/quote-comparison-browser');
await mkdir(artifacts, { recursive: true });
const report = { status: 'RUNNING', mockedApi: true, checks: [], pageErrors: [], requests: [],
  bundleIndexSHA256: createHash('sha256').update(await readFile(resolve(built, 'index.html'))).digest('hex'),
  knownGaps: ['Synthetic HTTP fixtures; no backend authorization, database or concurrency proof',
    'No real provider, gateway, pricing or forecast interaction'] };
let primary = 101, failRead = false, failWrite = false, holdWrite = false, mixedCurrency = false;
let releaseWrite, browser, server;
const quote = (id, currency) => ({ id, quoteNumber: `SYNTHETIC-Q-${id}`, name: id === 101 ? 'Good' : 'Better',
  status: id === 101 ? 'DRAFT' : 'ACCEPTED', dealType: 'NEW', currency, region: 'US', durationInMonths: 24,
  startDate: '2027-01-01', endDate: '2028-12-31', subtotal: id === 101 ? 1000 : 1500,
  totalDiscount: 0, totalTax: 0, totalAmount: id === 101 ? 1000 : 1500, arr: 12000, tcv: 24000, acv: 12000,
  rampScheduleStale: false, margin: { status: 'UNAVAILABLE', value: null, reason: 'A verified cost basis is not available' },
  lines: [{ productExternalId: 'SYNTHETIC-SUPPORT', productName: 'Synthetic support', productType: 'SAAS',
    lineItemType: 'SUBSCRIPTION', quantity: id === 101 ? 10 : 15, unitOfMeasure: 'SEAT', billingFrequency: 'MONTHLY' }] });
const comparison = () => ({ dealExternalId: 'SYNTHETIC-OPPORTUNITY', customerExternalId: 'SYNTHETIC-CUSTOMER',
  primaryQuoteId: primary, sameCurrency: !mixedCurrency, quotes: [quote(101, 'USD'), quote(102, mixedCurrency ? 'EUR' : 'USD')] });
const envelope = data => ({ success: true, timestamp: '2026-11-06T09:00:00Z', data });
const contentTypes = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css',
  '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon', '.woff2': 'font/woff2' };
function json(response, status, data) {
  response.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  response.end(JSON.stringify(data));
}

try {
  server = createServer(async (request, response) => {
    try {
      const path = new URL(request.url, 'http://127.0.0.1').pathname;
      if (path.startsWith('/api/')) report.requests.push({ method: request.method, path });
      if (path === '/api/v1/csrf') {
        response.writeHead(204, { 'Set-Cookie': 'XSRF-TOKEN=synthetic-comparison; Path=/; SameSite=Strict' });
        response.end(); return;
      }
      if (/^\/api\/v1\/quotes\/(101|102)\/comparison$/.test(path) && request.method === 'GET') {
        if (failRead) { failRead = false; json(response, 503, { success: false, message: 'Comparison is temporarily unavailable' }); }
        else json(response, 200, envelope(comparison()));
        return;
      }
      const selected = path.match(/^\/api\/v1\/quotes\/(101|102)\/primary$/);
      if (selected && request.method === 'POST') {
        for await (const chunk of request) { /* Consume the fixture request; never forward it. */ }
        assert.equal(request.headers['x-xsrf-token'], 'synthetic-comparison');
        if (failWrite) {
          failWrite = false;
          json(response, 409, { success: false, message: 'Primary preference was not changed', errorCode: 'SYNTHETIC_CONFLICT' });
        } else {
          primary = Number(selected[1]);
          if (holdWrite) { holdWrite = false; await new Promise(done => { releaseWrite = done; }); }
          json(response, 200, envelope(comparison()));
        }
        return;
      }
      if (path.startsWith('/api/')) { json(response, 503, { success: false, message: 'Outside this synthetic test' }); return; }
      const target = resolve(built, '.' + decodeURIComponent(path));
      assert.ok(target === built || target.startsWith(built + sep), 'Fixture static path stays inside built assets');
      const file = extname(path) ? target : resolve(built, 'index.html');
      const data = await readFile(file);
      response.writeHead(200, { 'Content-Type': contentTypes[extname(file)] ?? 'application/octet-stream' }); response.end(data);
    } catch (error) { if (!response.headersSent) json(response, 500, { message: String(error) }); else response.end(); }
  });
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const origin = `http://127.0.0.1:${server.address().port}`;
  browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  page.on('pageerror', error => report.pageErrors.push(error.message));
  await page.route('**/*', route => new URL(route.request().url()).origin === origin ? route.continue() : route.abort());
  const region = page.getByRole('region', { name: 'Quote alternatives table' });
  const mark = id => page.getByRole('button', { name: `Mark SYNTHETIC-Q-${id} primary`, exact: true });
  const preferred = id => page.getByRole('button', { name: `SYNTHETIC-Q-${id} is primary`, exact: true });
  const waitPreferred = async id => { await preferred(id).waitFor(); assert.ok(await preferred(id).isDisabled()); };
  await page.goto(`${origin}/quotes/101/compare`);
  await page.getByRole('heading', { name: 'Compare quote alternatives', exact: true }).waitFor();
  await region.waitFor();
  assert.ok(await region.locator('td').first().evaluate(element => parseFloat(getComputedStyle(element).paddingLeft)) >= 8,
    'Built comparison cells retain readable scoped spacing');
  assert.match(await region.innerText(), /USD 1,000\.00/);
  assert.match(await region.innerText(), /USD 1,500\.00/);
  assert.match(await region.innerText(), /A verified cost basis is not available/);
  assert.doesNotMatch(await region.innerText(), /40(?:\.0+)?\s*%/);
  await waitPreferred(101);
  report.checks.push('Actual lazy route renders totals, attributes and unavailable margin');

  holdWrite = true;
  await mark(102).focus(); await page.keyboard.press('Enter');
  await page.getByText('Saving primary preference…', { exact: true }).waitFor();
  assert.ok(await mark(102).isDisabled());
  assert.ok(await preferred(101).isDisabled());
  for (let attempt = 0; !releaseWrite && attempt < 200; attempt++) {
    await new Promise(done => setTimeout(done, 10));
  }
  assert.equal(report.requests.filter(request => request.method === 'POST').length, 1);
  assert.ok(releaseWrite, 'The synthetic response is held after committing the preference');
  releaseWrite(); releaseWrite = undefined;
  await waitPreferred(102);
  await page.reload(); await waitPreferred(102);
  report.checks.push('Keyboard primary change locks concurrent writes and survives a mocked reload');

  failWrite = true;
  await mark(101).click();
  await page.getByRole('alert').filter({ hasText: 'Primary preference was not changed' }).waitFor();
  assert.equal(primary, 102);
  await waitPreferred(102);
  report.checks.push('Refused primary write preserves the current preference');

  failRead = true;
  await page.reload();
  await page.getByRole('alert').filter({ hasText: 'Comparison is temporarily unavailable' }).waitFor();
  await page.getByRole('button', { name: 'Retry', exact: true }).click();
  await region.waitFor();
  report.checks.push('Read failure has a working retry');

  mixedCurrency = true;
  await page.reload(); await region.waitFor();
  await page.getByRole('note').filter({ hasText: 'Currencies differ or are unavailable' }).waitFor();
  assert.match(await region.innerText(), /EUR 1,500\.00/);
  await region.focus(); assert.ok(await region.evaluate(element => element === document.activeElement));
  await page.screenshot({ path: resolve(artifacts, 'comparison.png'), fullPage: true });
  report.checks.push('Different currencies remain labelled and the comparison region is keyboard reachable');
  assert.deepEqual(report.pageErrors, []);
  report.status = 'PASS';
} catch (error) {
  report.status = 'FAIL'; report.error = error.stack ?? String(error); process.exitCode = 1;
} finally {
  releaseWrite?.();
  await browser?.close();
  if (server) await new Promise(done => server.close(done));
  await writeFile(resolve(artifacts, 'report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
}
