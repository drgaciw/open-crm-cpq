/** Isolated feature host. Live mode uses real OMS HTTP/JWT/PostgreSQL; fixtures are explicit. */
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { mkdir, rm, writeFile } from 'node:fs/promises';
import assert from 'node:assert/strict';

const root = fileURLToPath(new URL('../../', import.meta.url));
const frontend = resolve(root, 'frontend');
const requireFrontend = createRequire(resolve(frontend, 'package.json'));
const { createServer } = await import(pathToFileURL(requireFrontend.resolve('vite')));
const fixtureMode = process.argv.includes('--fixtures');
const target = process.env.OMS_TEST_API_TARGET;
if (!fixtureMode && (!target || !process.env.OMS_TEST_TOKEN)) throw new Error('Live mode requires isolated OMS endpoint and test identity');
if (target && !['127.0.0.1', 'localhost'].includes(new URL(target).hostname)) throw new Error('Only a local test OMS endpoint is permitted');
process.chdir(frontend);
let server;
let browser;
let page;
const output = resolve(process.env.OMS_BROWSER_OUTPUT || resolve(root, 'backend/services/order-management/target'));
await mkdir(output, { recursive: true });
for (const name of ['oms-browser-live.json','oms-browser-live.png','oms-browser-failure.png','oms-browser-accessibility.json']) await rm(resolve(output,name),{force:true});
try {
server = await createServer({
  configFile: resolve(frontend, 'libs/cpq/orders/browser.vite.config.mts'),
  server: { port: Number(process.env.OMS_BROWSER_PORT || 0), strictPort: true,
    ...(target ? { proxy: { '/api': { target, changeOrigin: true } } } : {}) },
});
  await server.listen();
  const base = server.resolvedUrls.local[0];
  if (fixtureMode) {
    process.env.UAT_BASE_URL = base;
    await import(pathToFileURL(resolve(frontend, 'libs/cpq/orders/browser/final-flows.mjs')));
  } else {
    const { chromium } = requireFrontend('playwright');
    browser = await chromium.launch({ headless: true, ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}) });
    page = await browser.newPage({ extraHTTPHeaders: { Authorization: `Bearer ${process.env.OMS_TEST_TOKEN}` }, timezoneId: 'UTC' });
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    page.on('response', r => { if (r.status() >= 400 && r.url().includes('/api/')) console.error(`API failure ${r.status()} ${new URL(r.url()).pathname}`); });
    const commands = [];
    const post = async (button, suffix, expected = 200) => {
      const response = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(suffix));
      await page.getByRole('button', { name: button, exact: true }).click();
      const result = await response;
      if (result.status() !== expected) throw new Error(`${suffix}: expected ${expected}, got ${result.status()}: ${await result.text().catch(()=>'response body unavailable')}`);
      commands.push({ path: suffix, status: result.status() });
      // The response body is not read: Chromium cannot return it once the app has re-rendered (Network.getResponseBody
      // "No data found"), so callers take what they need from the UI, for example the new order's URL.
      await page.waitForFunction(() => !document.body.textContent.includes('Recording command…') && !document.body.textContent.includes('Loading order, execution plan'));
      if (suffix !== '/orders/from-quote') await page.getByRole('heading', { name: 'Accepted commercial snapshot', exact: true }).waitFor();
    };
    await page.goto(base + 'orders');
    await page.getByText('Create order from accepted quote', { exact: true }).click();
    await page.getByLabel('Snapshot ID', { exact: true }).fill('11111111-1111-4111-8111-111111111111');
    await page.getByLabel('Quote ID', { exact: true }).fill('Q-OMS-001');
    await page.getByLabel('Accepted revision', { exact: true }).fill('1');
    await post('Create order', '/orders/from-quote');
    // The app opens the new order's detail page; its id is the last path segment.
    await page.waitForURL(url => /\/orders\/[^/]+$/.test(new URL(url).pathname));
    const id = new URL(page.url()).pathname.split('/').pop();
    assert.ok(id, 'new order id from detail URL');
    await page.getByLabel('Command reason', { exact: true }).fill('Synthetic integrated pilot acceptance');
    await post('Submit order', '/submit');
    // Let the real server commit a hold, then lose its response. Reload must recover its receipt.
    let lostResponseDone;
    const lostResponse = new Promise((resolve, reject) => { lostResponseDone = { resolve, reject }; });
    await page.route('**/api/v1/orders/*/holds', async route => {
      try { const committed = await route.fetch(); assert.equal(committed.status(),200,'hold must commit before its response is lost'); await route.abort('failed'); lostResponseDone.resolve(); }
      catch (error) { lostResponseDone.reject(error); }
    }, { times: 1 });
    await page.getByLabel('Place hold (clear to release)').check();
    await page.getByRole('button', { name: 'Apply hold change', exact: true }).click();
    await lostResponse;
    await page.getByText('An earlier command outcome is unresolved.', { exact: false }).waitFor();
    await page.reload();
    await page.getByRole('region', { name: 'Active holds' }).waitFor();
    await page.waitForFunction(() => !document.body.textContent.includes('An earlier command outcome is unresolved.'));
    await page.getByLabel('Command reason', { exact: true }).fill('Synthetic hold cleared after durable receipt reconciliation');
    await page.getByLabel('Place hold (clear to release)').uncheck();
    await post('Apply hold change', '/holds');
    const at = new Date(Date.now() - 120000).toISOString().slice(0,16);
    const later = new Date(Date.now() - 60000).toISOString().slice(0,16);
    const evidence = async (kind, qty, ref, causal, expected = 200) => {
      await page.getByLabel('Evidence line').selectOption({ index: 1 });
      await page.getByLabel('Evidence kind').selectOption(kind);
      await page.getByLabel('Evidence quantity', { exact: true }).fill(String(qty));
      await page.getByLabel('External reference', { exact: true }).fill(ref);
      await page.getByLabel('Occurred at (local time)', { exact: true }).fill(kind === 'DELIVERY' ? later : at);
      await page.getByLabel('Evidence reason', { exact: true }).fill('Synthetic evidence for isolated browser verification');
      if (kind === 'ALLOCATION') await page.getByLabel('Allocation expiry (local time)', { exact: true }).fill(new Date(Date.now()+86400000).toISOString().slice(0,16));
      if (kind === 'SHIPMENT') await page.getByLabel('Original allocation reference', { exact: true }).fill(causal);
      if (kind === 'DELIVERY') await page.getByLabel('Causal shipment reference', { exact: true }).fill(causal);
      await post('Record evidence', '/evidence', expected);
    };
    await evidence('DELIVERY',4,'delivery-first','shipment-first',202);
    await evidence('ALLOCATION',10,'allocation-browser');
    await evidence('SHIPMENT',4,'shipment-first','allocation-browser');
    assert.equal(await page.locator('.badge').filter({ hasText: /^COMPLETED$/ }).count(),0,'partial delivery cannot complete order');
    await evidence('SHIPMENT',6,'shipment-second','allocation-browser');
    await evidence('DELIVERY',6,'delivery-second','shipment-second');
    await page.getByLabel('Finance line').selectOption({ index: 1 });
    await page.getByLabel('Finance reason', { exact: true }).fill('Synthetic external finance authority');
    await post('Prepare finance request', '/finance-requests');
    for (const [qty, ref] of [[4,'finance-part-1'],[6,'finance-part-2']]) {
      await page.getByLabel('Recorded finance request').selectOption({ index: 1 });
      await page.getByLabel('Finance quantity', { exact: true }).fill(String(qty));
      await page.getByLabel('Finance external reference', { exact: true }).fill(ref);
      await page.getByLabel('Finance occurred at (local time)', { exact: true }).fill(later);
      await post('Record actual finance outcome', '/finance-acknowledgments');
      if (qty === 4) assert.equal(await page.locator('.badge').filter({ hasText: /^COMPLETED$/ }).count(),0,'partial acknowledgment cannot complete order');
    }
    await page.locator('.badge').filter({ hasText: /^COMPLETED$/ }).waitFor();
    let axePath;
    try { axePath = requireFrontend.resolve('axe-core/axe.min.js'); }
    catch { axePath = resolve(frontend, '.cache/oms-browser-tools/node_modules/axe-core/axe.min.js'); }
    await page.addScriptTag({ path: axePath });
    const accessibility = await page.evaluate(async () => window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a','wcag2aa','wcag21aa'] } }));
    await writeFile(resolve(output, 'oms-browser-accessibility.json'), JSON.stringify(accessibility, null, 2));
    assert.deepEqual(accessibility.violations.map(v => ({ id:v.id, impact:v.impact, nodes:v.nodes.length })), [], 'automated WCAG findings');
    await page.getByRole('link', { name: 'All orders', exact: true }).focus();
    await page.keyboard.press('Tab');
    assert.equal(await page.evaluate(() => document.activeElement?.textContent?.trim()), 'Exception queue');
    await page.screenshot({ path: resolve(output,'oms-browser-live.png'), fullPage:true });
    assert.deepEqual(errors, []);
    await writeFile(resolve(output,'oms-browser-live.json'),JSON.stringify({ orderId:id, commands, lostResponseRecovered:true, keyboardProbe:true, automatedAccessibilityViolations:0, browserErrors:errors, limitations:['Synthetic authorities; no production provider effects','Direct OMS HTTP; production gateway identity and manual screen-reader checks require deployment verification'] },null,2));
    console.log(`Live OMS browser acceptance passed: ${commands.length} commands, lost-response recovery, partial fulfillment and finance acknowledgment.`);
  }
} catch (error) {
  if (page && !page.isClosed()) { await page.screenshot({ path:resolve(output,'oms-browser-failure.png'),fullPage:true }).catch(()=>{}); console.error((await page.locator('body').innerText().catch(()=>'' )).slice(0,5000)); }
  throw error;
} finally { try { await browser?.close(); } finally { await server?.close(); } }
