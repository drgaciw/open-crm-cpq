/**
 * DRG-633/634/635 browser acceptance with synthetic data and a real private quote service.
 * Requires a built frontend and a separately started, bounded quote-service on loopback
 * 18484, backed by the wave-owned cpq_ramp_uat PostgreSQL database. Its launcher must
 * record {worktree,pid,jar,port:18484,database:'cpq_ramp_uat'} in the ignored file
 * .gitnexus/browser-service-process.json. This script never starts or stops that service.
 *
 * Set CPQ_RAMP_FOLLOWUP_UAT=1 and CPQ_RAMP_UAT_ROOT to the explicit isolated checkout,
 * then run: node scripts/verification/ramp-followups-browser-acceptance.mjs <checkout>.
 * Uses frontend's installed Playwright and bundled Chromium. Only new synthetic quote
 * IDs are edited. A private static SPA/proxy on 18490 and browser close in finally.
 * JSON and screenshots go to .gitnexus/browser-artifacts. Existing committed proxy
 * configuration is untouched. The test proxy supplies a synthetic XSRF cookie; gateway
 * routing, authenticated identity, pricing, approval and event delivery remain outside
 * this scoped direct quote-service proof. It is not a complete CPQ lifecycle UAT.
 */
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, realpath, mkdir, writeFile } from 'node:fs/promises';
import { resolve, sep, extname } from 'node:path';
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';

// Run only against an explicitly named isolated checkout and its recorded service.
const root = await realpath(resolve(process.argv[2] || process.cwd()));
assert.ok(process.env.CPQ_RAMP_UAT_ROOT, 'CPQ_RAMP_UAT_ROOT must explicitly name the isolated checkout');
assert.equal(root, await realpath(process.env.CPQ_RAMP_UAT_ROOT));
assert.equal(process.env.CPQ_RAMP_FOLLOWUP_UAT, '1', 'Explicit isolated synthetic-data sentinel required');
const ownedService = JSON.parse(await readFile(resolve(root, '.gitnexus/browser-service-process.json'), 'utf8'));
assert.equal(await realpath(ownedService.worktree), root);
assert.equal(ownedService.port, 18484);
assert.equal(ownedService.database, 'cpq_ramp_uat');
assert.equal(await realpath(ownedService.jar), await realpath(resolve(root, 'backend/services/quote-service/target/quote-service-1.0.0-SNAPSHOT.jar')));
assert.ok(Number.isSafeInteger(ownedService.pid) && ownedService.pid > 0);
process.kill(ownedService.pid, 0);
const api = 'http://127.0.0.1:18484';
const origin = 'http://127.0.0.1:18490';
const staticRoot = await realpath(resolve(root, 'frontend/dist/apps/crm/browser'));
const artifacts = resolve(root, '.gitnexus/browser-artifacts');
await mkdir(artifacts, { recursive: true });
const report = { startedUtc: new Date().toISOString(), api, origin, status: 'RUNNING', checks: [],
  servicePid: ownedService.pid, sourceCommit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim(),
  worktreeDirty: execFileSync('git', ['status', '--porcelain'], { cwd: root, encoding: 'utf8' }).trim().length > 0,
  bundleIndexSha256: createHash('sha256').update(await readFile(resolve(staticRoot, 'index.html'))).digest('hex'),
  pageErrors: [], http: [], knownGaps: ['Gateway routing, CSRF enforcement and authenticated identity are outside the direct quote-service path',
    'Pricing calculation, approval routing, outbox delivery and downstream services are not exercised',
    'The static test server supplies a synthetic XSRF cookie only to allow the normal frontend interceptor; domain security uses isolated dev mode'] };
let pauseNextExtension = false;
let extensionCommitted;
let releaseExtension;
let browser;
let page;
let server;
const request = async (method, path, body) => {
  const response = await fetch(api + path, { method, headers: { 'Content-Type': 'application/json' },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }), signal: AbortSignal.timeout(30000) });
  const value = response.status === 204 ? null : await response.json();
  assert.ok(response.ok, `${method} ${path}: ${response.status} ${JSON.stringify(value)}`);
  return value?.data ?? value;
};
const quotePath = id => `/api/v1/quotes/${id}`;
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json',
  '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.png': 'image/png', '.ico': 'image/x-icon' };
try {
  const runId = `RAMP-FOLLOWUP-UAT-${Date.now()}`;
  const fixture = await request('POST', '/api/v1/quotes', { name: runId, customerExternalId: `SYNTHETIC-${runId}`,
    currency: 'USD', region: 'US', durationInMonths: 26, dealType: 'NEW' });
  const line = await request('POST', `${quotePath(fixture.id)}/line-items`, { productExternalId: `SYNTHETIC-SEAT-${runId}`,
    productName: 'Synthetic browser seats', productType: 'SAAS', lineItemType: 'SUBSCRIPTION', quantity: 2,
    unitOfMeasure: 'SEAT', listPrice: 100, billingFrequency: 'MONTHLY', notes: 'Synthetic fixture; no customer data' });
  report.fixture = { runId, quoteId: fixture.id, lineId: line.id };
  server = createServer(async (incoming, outgoing) => {
    try {
      const url = new URL(incoming.url, origin);
      if (url.pathname === '/api/v1/csrf') {
        outgoing.writeHead(204, { 'Set-Cookie': 'XSRF-TOKEN=synthetic-ramp-uat; Path=/; SameSite=Strict' });
        outgoing.end(); return;
      }
      if (url.pathname.startsWith('/api/')) {
        if (!/^\/api\/v1\/quotes(?:\/|$)/.test(url.pathname)) {
          outgoing.writeHead(503, { 'Content-Type': 'application/json' });
          outgoing.end(JSON.stringify({ success: false, message: 'Outside scoped direct-service UAT' })); return;
        }
        const chunks = []; for await (const chunk of incoming) chunks.push(chunk);
        const body = Buffer.concat(chunks);
        const response = await fetch(api + url.pathname + url.search, { method: incoming.method,
          headers: { 'Content-Type': incoming.headers['content-type'] || 'application/json' },
          ...(body.length ? { body } : {}), signal: AbortSignal.timeout(30000) });
        const bytes = Buffer.from(await response.arrayBuffer());
        report.http.push({ method: incoming.method, path: url.pathname, status: response.status,
          ...(url.pathname.endsWith('/ramp-schedule/expand') ? { request: JSON.parse(body.toString()), response: JSON.parse(bytes.toString()) } : {}) });
        if (pauseNextExtension && response.ok && incoming.method === 'POST' && url.pathname.endsWith('/ramp-schedule/extend')) {
          pauseNextExtension = false;
          const held = new Promise(resolveHold => { releaseExtension = resolveHold; });
          extensionCommitted?.(JSON.parse(bytes.toString()));
          await held;
        }
        outgoing.writeHead(response.status, { 'Content-Type': response.headers.get('content-type') || 'application/json', 'Cache-Control': 'no-store' });
        outgoing.end(bytes); return;
      }
      let file = resolve(staticRoot, `.${decodeURIComponent(url.pathname)}`);
      if (file !== staticRoot && !file.startsWith(staticRoot + sep)) throw new Error('Invalid static path');
      try { file = await realpath(file); } catch { file = resolve(staticRoot, 'index.html'); }
      if (!file.startsWith(staticRoot + sep)) file = resolve(staticRoot, 'index.html');
      let bytes; try { bytes = await readFile(file); } catch { file = resolve(staticRoot, 'index.html'); bytes = await readFile(file); }
      outgoing.writeHead(200, { 'Content-Type': types[extname(file)] || 'application/octet-stream',
        'Cache-Control': 'no-store', 'Set-Cookie': 'XSRF-TOKEN=synthetic-ramp-uat; Path=/; SameSite=Strict' });
      outgoing.end(bytes);
    } catch (error) {
      outgoing.writeHead(502, { 'Content-Type': 'application/json' });
      outgoing.end(JSON.stringify({ success: false, message: error.message }));
    }
  });
  await new Promise((accept, reject) => { server.once('error', reject); server.listen(18490, '127.0.0.1', accept); });
  const { chromium } = createRequire(resolve(root, 'frontend/package.json'))('playwright');
  browser = await chromium.launch({ headless: true });
  page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  page.setDefaultTimeout(20000);
  page.on('pageerror', error => report.pageErrors.push(error.message));
  await page.route('**/*', route => {
    const url = new URL(route.request().url());
    return url.origin === origin ? route.continue() : route.abort();
  });
  const waitValue = async (testId, value) => {
    await page.waitForFunction(({ testId, value }) => document.querySelector(`[data-testid="${testId}"]`)?.value === String(value), { testId, value });
  };
  await page.goto(`${origin}/quotes/${fixture.id}`);
  await page.getByTestId('ramp-editor').waitFor();
  await page.getByTestId('escalator-start').fill('100');
  await page.getByTestId('escalator-amount').fill('10');
  await page.getByTestId('escalator-every').fill('12');
  let responsePromise = page.waitForResponse(response => response.url().endsWith('/ramp-schedule/expand'));
  await page.getByTestId('expand-escalator').click();
  assert.ok((await responsePromise).ok());
  await waitValue('period-unit-2', 121);
  report.checks.push('Legacy default compounding produces 100,110,121');
  await page.getByTestId('escalator-compounding').click();
  await page.getByRole('option', { name: 'Increase from the start price', exact: true }).click();
  responsePromise = page.waitForResponse(response => response.url().endsWith('/ramp-schedule/expand'));
  await page.getByTestId('expand-escalator').click();
  assert.ok((await responsePromise).ok());
  for (const [index, price] of [100, 110, 120].entries()) await waitValue(`period-unit-${index}`, price);
  for (const [index, months] of [12, 12, 2].entries()) await waitValue(`period-months-${index}`, months);
  report.checks.push('Noncompounding generation produces100,110,120 and complete26-month12+12+2 term');
  responsePromise = page.waitForResponse(response => response.request().method() === 'PUT' && response.url().endsWith(`/line-items/${line.id}`));
  await page.getByTestId('save-schedule').click();
  assert.ok((await responsePromise).ok());
  await page.reload();
  await waitValue('period-unit-2', 120);
  assert.match(await page.getByTestId('escalator-compounding').innerText(), /Increase from the start price/);
  let persisted = await request('GET', quotePath(fixture.id));
  assert.equal(persisted.durationInMonths, 26);
  assert.equal(persisted.lineItems[0].schedule.escalator.compounding, false);
  assert.deepEqual(persisted.lineItems[0].schedule.steps.map(step => step.months), [12, 12, 2]);
  report.checks.push('Actual quote-service/PostgreSQL save+hard reload retains option,false and short final period');
  await page.screenshot({ path: resolve(artifacts, '01-noncompounding-reload.png'), fullPage: true });
  await page.getByTestId('extend-duration').fill('38');
  pauseNextExtension = true;
  const committed = new Promise(resolveCommit => { extensionCommitted = resolveCommit; });
  responsePromise = page.waitForResponse(response => response.url().endsWith('/ramp-schedule/extend'));
  await page.getByTestId('extend-escalator').click();
  let commitTimer;
  const heldResponse = await Promise.race([
    committed,
    responsePromise.then(response => {
      assert.ok(response.ok(), `Extension failed before held commit: HTTP ${response.status()}`);
      return committed;
    }),
    new Promise((_, reject) => {
      commitTimer = setTimeout(() => reject(new Error('Timed out waiting for the isolated extension commit')), 30000);
    }),
  ]).finally(() => clearTimeout(commitTimer));
  assert.equal(heldResponse.data.durationInMonths, 38);
  assert.equal(await page.getByTestId('period-unit-0').isDisabled(), true);
  assert.equal(await page.getByTestId('ramp-line').getAttribute('aria-disabled'), 'true');
  persisted = await request('GET', quotePath(fixture.id));
  assert.equal(persisted.durationInMonths, 38);
  assert.deepEqual(persisted.lineItems[0].schedule.steps.map(step => step.months), [12, 12, 2, 12]);
  releaseExtension(); releaseExtension = undefined;
  assert.ok((await responsePromise).ok());
  await waitValue('period-unit-3', 130);
  await page.getByText('38 months', { exact: true }).waitFor();
  assert.equal(await page.getByTestId('period-unit-0').isDisabled(), false);
  report.checks.push('Held real committed extend response locks controls and reconciles quote term+schedule when released');
  await page.reload();
  await waitValue('period-unit-3', 130);
  await page.getByText('38 months', { exact: true }).waitFor();
  report.checks.push('Atomic extension persists38-month quote and100,110,120,130 schedule across hard reload');
  await page.screenshot({ path: resolve(artifacts, '02-extended-reload.png'), fullPage: true });
  const beforeConflict = await request('GET', quotePath(fixture.id));
  await request('PUT', quotePath(fixture.id), { notes: `Synthetic concurrent header edit ${Date.now()}` });
  await page.getByTestId('extend-duration').fill('50');
  responsePromise = page.waitForResponse(response => response.url().endsWith('/ramp-schedule/extend'));
  await page.getByTestId('extend-escalator').click();
  assert.equal((await responsePromise).status(), 409);
  await page.getByRole('alert').filter({ hasText: 'QUOTE_VERSION_CONFLICT' }).waitFor();
  persisted = await request('GET', quotePath(fixture.id));
  assert.ok(persisted.version > beforeConflict.version);
  assert.equal(persisted.durationInMonths, 38);
  assert.deepEqual(persisted.lineItems[0].schedule.steps.map(step => step.months), [12, 12, 2, 12]);
  report.checks.push('Real stale browser version returns409QUOTE_VERSION_CONFLICT and preserves stored38-month schedule');
  await page.screenshot({ path: resolve(artifacts, '03-version-conflict.png'), fullPage: true });
  assert.deepEqual(report.pageErrors, [], 'Unhandled browser errors');
  assert.ok(report.http.every(item => item.status < 400 || item.status === 409), 'Unexpected quote API failure');
  report.status = 'PASS';
} catch (error) {
  report.status = 'FAIL'; report.failure = error.stack;
  if (page) {
    report.failureInputs = await page.locator('[data-testid]').evaluateAll(elements => elements.filter(element => 'value' in element).map(element => ({ id: element.getAttribute('data-testid'), value: element.value }))).catch(() => []);
    await page.screenshot({ path: resolve(artifacts, 'failure.png'), fullPage: true }).catch(() => {});
  }
  process.exitCode = 1;
} finally {
  releaseExtension?.();
  if (browser) await browser.close();
  if (server) { server.closeAllConnections(); await new Promise(accept => server.close(accept)); }
  report.finishedUtc = new Date().toISOString();
  await writeFile(resolve(artifacts, 'report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
}
