import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { startRampAcceptanceServer } from './ramp-browser-acceptance.server.mjs';

export async function runRampBrowserAcceptance() {
  const root = resolve(process.argv[2] || '');
  const artifacts = resolve(root, '.acceptance-artifacts');
  const evidence = JSON.parse(await readFile(resolve(artifacts, 'ramp-r1-live-acceptance-result.json'), 'utf8'));
  assert.equal(evidence.database, 'ramp_wave_20261009');
  assert.equal(evidence.origins.quote, 'http://127.0.0.1:18084');
  assert.match(evidence.runId, /^RAMP-AC01-\d+$/);
  for (const id of [evidence.quoteId, evidence.governedQuoteId]) assert.ok(Number.isSafeInteger(Number(id)) && Number(id) > 0);
  if (process.env.RAMP_ACCEPTANCE_DATABASE !== evidence.database) throw new Error('Verify isolated DB and set acceptance sentinel');
  const freeGB = Number(execFileSync('powershell.exe', ['-NoProfile', '-Command', '(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB'], { encoding: 'utf8' }).trim());
  assert.ok(freeGB >= 1, 'Need at least 1GB free RAM before adding browser/server to the isolated stack');
  for (const id of [evidence.quoteId, evidence.governedQuoteId]) {
    const response = await fetch(`${evidence.origins.quote}/api/v1/quotes/${id}`, { signal: AbortSignal.timeout(10000) });
    assert.ok(response.ok, 'Actual persisted quote must be reachable before browser launch');
    const persisted = (await response.json()).data;
    assert.ok(persisted.name.includes(evidence.runId), 'Only the API fixture synthetic quotes may be edited');
    assert.equal(persisted.lineItems.length, 1);
    assert.equal(persisted.lineItems[0].schedule.steps.length, 3);
    if (Number(id) === Number(evidence.governedQuoteId)) {
      assert.equal(persisted.status, 'DRAFT', 'Scoped browser fixture must remain a draft quote');
      assert.equal(persisted.approvalState, 'PENDING', 'Scoped draft fixture must have pending governance');
    }
  }
  const require = createRequire(resolve(process.argv[3] || resolve(root, 'frontend'), 'package.json'));
  const { chromium } = require('playwright');
  const server = await startRampAcceptanceServer(root);
  const report = { checks: [], pageErrors: [], consoleErrors: [], failedResponses: [], knownGaps: ['gateway and authenticated identity', 'canonical approval inbox and event consumption', 'actual PENDING_APPROVAL-status edit gate (unit coverage only)', 'complete submit/send/accept lifecycle', 'unrelated CRM/catalog dependencies'] };
  let browser;
  try {
    browser = await chromium.launch({ headless: true });
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    page.setDefaultTimeout(15000);
    page.on('pageerror', error => report.pageErrors.push(error.message));
    page.on('console', message => { if (message.type() === 'error') report.consoleErrors.push(message.text()); });
    page.on('response', response => { if (response.status() >= 400) report.failedResponses.push({ url: response.url(), status: response.status() }); });
    await page.route('**/*', route => new URL(route.request().url()).origin === server.origin ? route.continue() : route.abort());
    const assertDisabled = async name => assert.equal(await page.getByRole('button', { name, exact: true }).isDisabled(), true, `${name} must be disabled`);
    await page.goto(`${server.origin}/quotes/${evidence.quoteId}`);
    for (let period = 1; period <= 3; period++) {
      const expected = [40, 45, 50][period - 1];
      await page.getByTestId(`preview-net-${period}`).waitFor();
      assert.match(await page.getByTestId(`preview-net-${period}`).innerText(), new RegExp(`\\b${expected}\\.00\\b`));
    }
    assert.match(await page.getByTestId('metric-tcv').innerText(), /81,000\.00/);
    assert.match(await page.getByTestId('metric-acv').innerText(), /27,000\.00/);
    await page.reload(); await page.getByTestId('preview-total-3').waitFor();
    assert.match(await page.getByTestId('preview-total-3').innerText(), /30,000\.00/);
    report.checks.push('Real persisted AC01 rates/metrics/preview after hard reload');
    await page.screenshot({ path: resolve(artifacts, 'ramp-browser-ac01.png'), fullPage: true });
    await page.goto(`${server.origin}/quotes/${evidence.governedQuoteId}`);
    const banner = page.getByTestId('ramp-governance-banner'); await banner.waitFor();
    const bannerText = await banner.innerText();
    assert.match(bannerText, /32/); assert.match(bannerText, /period:\s*1/i); assert.match(bannerText, /PENDING/); assert.match(bannerText, /Sales VP|SALES_VP/i);
    await assertDisabled('Send'); await assertDisabled('Accept');
    report.checks.push('Real AC05 DRAFT quote with pending governance banner and downstream actions blocked');
    // Same persisted schedule save must not invalidate priced data or governance.
    const identicalSaved = page.waitForResponse(response => response.url().includes('/line-items/') && response.request().method() === 'PUT');
    await page.getByTestId('save-schedule').click(); assert.ok((await identicalSaved).ok());
    await page.reload(); await banner.waitFor();
    assert.equal(await page.getByTestId('ramp-stale-banner').count(), 0);
    report.checks.push('Identical DRAFT save with pending governance preserves priced binding');
    // Change a non-governing period while maintaining the worst 32% discount.
    await page.getByTestId('period-unit-1').fill('44');
    const saved = page.waitForResponse(response => response.url().includes('/line-items/') && response.request().method() === 'PUT');
    await page.getByTestId('save-schedule').click(); assert.ok((await saved).ok());
    await page.reload(); await page.getByTestId('ramp-stale-banner').waitFor();
    await assertDisabled('Submit'); await assertDisabled('Send'); await assertDisabled('Accept');
    assert.equal(await banner.count(), 0);
    assert.match(await page.getByTestId('preview-net-2').innerText(), /—/);
    await page.screenshot({ path: resolve(artifacts, 'ramp-browser-stale.png'), fullPage: true });
    report.checks.push('Real DRAFT edit with pending governance persists stale state and hides old preview/governance');
    const recalculated = page.waitForResponse(response => response.url().endsWith('/calculate') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Calculate', exact: true }).click(); assert.ok((await recalculated).ok());
    await page.reload(); await banner.waitFor();
    assert.match(await page.getByTestId('preview-net-2').innerText(), /44\.00/);
    assert.match(await banner.innerText(), /32/);
    await assertDisabled('Send'); await assertDisabled('Accept');
    report.checks.push('Real recalculation persists changed period and current pending governance');
    await page.screenshot({ path: resolve(artifacts, 'ramp-browser-recalculated.png'), fullPage: true });
    assert.deepEqual(report.pageErrors, [], 'Unhandled browser errors');
    assert.ok(!report.failedResponses.some(item => /\/api\/v1\/quotes(?:\/|$)/.test(new URL(item.url).pathname)), 'Quote HTTP requests must not fail');
    report.status = 'PASS';
  } catch (error) { report.status = 'FAIL'; report.failure = error.message; throw error; }
  finally {
    try { await mkdir(artifacts, { recursive: true }); await writeFile(resolve(artifacts, 'ramp-browser-result.json'), JSON.stringify(report, null, 2)); }
    finally { try { if (browser) await browser.close(); } finally { await server.close(); } }
  }
}

await runRampBrowserAcceptance();
