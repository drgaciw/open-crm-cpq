/**
 * Asset Lifecycle Management UAT (UAT-ALM-01..08) against http://127.0.0.1:4200.
 *
 * Spec: docs/uat/asset-lifecycle-management-cpq.md
 * Read probes require successful, correctly shaped responses; they do not prove lifecycle semantics.
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, gap, blocked } from '../../lib/health.mjs';
import { readAssetResponse } from '../../lib/asset-observations.mjs';
import { ensure, uniqueName } from '../fixtures.mjs';

const ASSETS = '/api/v1/assets';
const REPORTS = '/api/v1/assets/reports';
const QUOTES = '/api/v1/quotes';

export default {
  id: 'asset-lifecycle',

  async run(ctx) {
    const { page, api } = ctx;

    // UAT-ALM-01 — Quotes / Deals shells
    await runCheck(ctx, 'asset-lifecycle.ui.quotes-shell', async () => {
      await goto(page, '/quotes');
      await page.getByRole('heading', { level: 1 }).first().waitFor({ state: 'visible', timeout: 15000 });
      const title = (await page.getByRole('heading', { level: 1 }).first().innerText()).trim();
      if (!/quote/i.test(title)) throw new Error(`expected Quotes heading, got "${title}"`);
      return title;
    });

    await runCheck(ctx, 'asset-lifecycle.ui.deals-shell', async () => {
      await goto(page, '/deals');
      await page.getByRole('heading', { level: 1 }).first().waitFor({ state: 'visible', timeout: 15000 });
      const title = (await page.getByRole('heading', { level: 1 }).first().innerText()).trim();
      if (!/deal|opportunit/i.test(title)) throw new Error(`expected Deals heading, got "${title}"`);
      return title;
    });

    // These are response-shape probes, not proof of authorization or lifecycle invariants.
    await runCheck(ctx, 'asset-lifecycle.api.assets-up', async () => {
      await readAssetResponse(api, `${ASSETS}?accountId=uat-alm&asOf=2026-01-01`, 'installed base', Array.isArray);
      return 'installed-base response is an array; no asset lifecycle semantics checked';
    });

    await runCheck(ctx, 'asset-lifecycle.api.history-shape', async () => {
      await readAssetResponse(api, `${ASSETS}/does-not-exist/history`, 'history', Array.isArray);
      return 'history response is an array; no history reconstruction checked';
    });

    await runCheck(ctx, 'asset-lifecycle.api.reports-up', async () => {
      const account = 'uat-alm';
      await readAssetResponse(api, `${REPORTS}/upcoming-renewals?accountId=${account}&withinDays=30`,
        'upcoming renewals', (x) => x && x.accountId === account && Array.isArray(x.assets));
      await readAssetResponse(api, `${REPORTS}/churn?accountId=${account}&periodStart=2026-01-01&periodEnd=2026-12-31`,
        'churn', (x) => x && x.accountId === account && Array.isArray(x.events));
      return 'report response shapes verified; report calculations not checked';
    });

    // UAT-ALM-04 — Quote create precursor via API fixture + UI open
    await runCheck(ctx, 'asset-lifecycle.ui.quote-detail', async () => {
      const quote = await ensure(ctx, 'quote');
      await goto(page, `/quotes/${quote.id}`);
      await page.getByRole('heading', { level: 1 }).first().waitFor({ state: 'visible', timeout: 15000 });
      const dto = await api.get(`${QUOTES}/${quote.id}`);
      if (dto.status !== 'DRAFT') throw new Error(`expected DRAFT, got ${dto.status}`);
      return `${dto.quoteNumber ?? quote.id} DRAFT`;
    });

    // UAT-ALM-05 — Renewal-from-assets UI control (GAP if absent)
    await runCheck(ctx, 'asset-lifecycle.ui.renewal-from-assets', async () => {
      await goto(page, '/quotes/new');
      await page.getByRole('heading', { level: 1 }).first().waitFor({ state: 'visible', timeout: 15000 });
      throw gap('Renewal-from-assets behavior is not exercised by this suite; a visible label alone is not evidence of a working flow');
    });

    // UAT-ALM-06 — Installed-base nav (GAP if absent)
    await runCheck(ctx, 'asset-lifecycle.ui.installed-base-nav', async () => {
      await goto(page, '/dashboard');
      await page.getByRole('heading', { level: 1 }).first().waitFor({ state: 'visible', timeout: 15000 }).catch(() => null);
      throw gap('Installed-base page behavior is not exercised by this suite; an SPA HTTP 200 does not prove a route exists');
    });

    // A read probe and source-code inspection cannot prove deployed apply behavior.
    await runCheck(ctx, 'asset-lifecycle.hold.armed', async () => {
      throw blocked('No safe apply fixture or deployed readiness signal is available to this suite');
    });

    await runCheck(ctx, 'asset-lifecycle.null-start.unit-gate', async () => {
      throw blocked('The browser suite does not run the null-start test or verify ledger state; run the backend test separately');
    });

    // Extra: create naming hygiene for any follow-on asset seed
    await runCheck(ctx, 'asset-lifecycle.fixture.prefix', async () => {
      const name = uniqueName(ctx.runId, 'alm');
      if (!name.startsWith(`UAT-${ctx.runId}-`)) throw new Error(`bad prefix ${name}`);
      return name;
    });
  },
};
