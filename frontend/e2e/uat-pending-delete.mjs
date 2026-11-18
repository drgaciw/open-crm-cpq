/** Narrow DRG-758 live runner. See uat/pending-delete.md for isolated-stack/session prerequisites. */
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { BASE, launch } from './lib/browser.mjs';
import { createApi } from './lib/api.mjs';
import { requireDistinctGatewaySessions } from './lib/pending-delete-session.mjs';
import { createReport } from './lib/report.mjs';
import { monitorPage, runCheck } from './lib/health.mjs';
import { newRunId } from './uat/fixtures.mjs';
import { pendingDeleteRefusal } from './uat/workflows/pending-delete.mjs';

const report = createReport('drg-758-browser', { strict: true });
const runId = newRunId();
let browser;
let api;
let passed = false;
try {
  if (process.env.UAT_ISOLATED_FIXTURES !== '1'
      || !['localhost', '127.0.0.1', '[::1]'].includes(new URL(BASE).hostname)) {
    throw new Error('BLOCKED: set UAT_ISOLATED_FIXTURES=1 only for an isolated loopback CRM/CPQ stack');
  }
  const sellerActor = process.env.UAT_SELLER_ACTOR;
  const approverActor = process.env.UAT_APPROVER_ACTOR;
  if (!sellerActor || !approverActor || sellerActor === approverActor) {
    throw new Error('BLOCKED: distinct synthetic UAT_SELLER_ACTOR and UAT_APPROVER_ACTOR are required');
  }
  const loadSession = (key) => {
    if (!process.env[key]) throw new Error(`BLOCKED: ${key} must point to an authenticated Playwright storage state`);
    try {
      return JSON.parse(readFileSync(process.env[key], 'utf8'));
    } catch {
      throw new Error(`BLOCKED: ${key} could not be read as JSON storage state`);
    }
  };
  const sellerState = loadSession('UAT_SELLER_STORAGE_STATE');
  const approverState = loadSession('UAT_APPROVER_STORAGE_STATE');
  requireDistinctGatewaySessions(sellerState, approverState, {
    base: BASE, cookieName: process.env.UAT_SESSION_COOKIE_NAME,
  });
  const session = await launch({ contextOptions: { storageState: sellerState } });
  browser = session.browser;
  const approverContext = await browser.newContext({ storageState: approverState });
  const approverPage = await approverContext.newPage();
  approverPage.setDefaultTimeout(30000);
  api = createApi(session.page.request);
  const approverApi = createApi(approverPage.request);
  // Read-only availability probes only; no empty pricing/configuration POST probes.
  const preflight = {};
  for (const [key, path] of Object.entries({ quote: '/quotes', product: '/products',
    pricebook: '/price-books', pricing: '/pricing-customers', approvalsBff: '/quotes/approvals/pending' })) {
    const response = await session.page.request.get(`${BASE}/api/v1${path}`, { timeout: 8000 });
    preflight[key] = { state: response.ok() ? 'up' : 'down', status: response.status() };
  }
  const ctx = { page: session.page, api, report, runId, preflight, base: BASE, isolatedFixtures: true,
    sellerActor, approverActor, approver: { page: approverPage, api: approverApi },
    monitor: monitorPage(session.page), evidenceDir: resolve('.ci-tmp/uat-full/evidence') };
  passed = await runCheck(ctx, 'approvals.pending.delete-refused-keeps-request', async () => {
    const result = await pendingDeleteRefusal(ctx);
    return result;
  }, { expectedErrors: ['409 (Conflict)'] });
} catch (error) {
  // Do not serialize storage state, credentials or HTTP bodies into the report.
  report.blocked('drg-758.prerequisites', error.message);
} finally {
  // Preserve pending approval evidence and its dependencies. Never call the broad residue purge
  // or cancel/decide requests. Only this run's exact fixture ledger is reported for stack disposal.
  for (const entry of api?.ledger ?? []) report.residue({ ...entry, reason: 'isolated fixture retained for evidence; dispose of isolated test stack after review' });
  await browser?.close();
}
report.finish({ runId, liveBrowserPassed: passed });
process.exitCode = passed ? 0 : 1;
