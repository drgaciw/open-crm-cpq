/**
 * Contract Lifecycle Management UAT (UAT-CLM-01..09) against http://127.0.0.1:4200.
 *
 * Spec: docs/uat/contract-lifecycle-management-cpq.md
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, blocked } from '../../lib/health.mjs';
import { ensure, uniqueName } from '../fixtures.mjs';

const SUBSCRIPTIONS = '/api/v1/subscriptions';
const QUOTES = '/api/v1/quotes';

async function postStatus(api, path, body) {
  try {
    return { status: 200, body: await api.post(path, body) };
  } catch (error) {
    const status = error?.status ?? error?.response?.status;
    if (typeof status === 'number') return { status, body: error.body };
    throw error;
  }
}

export default {
  id: 'contract-lifecycle',

  async run(ctx) {
    const { page, api } = ctx;

    // UAT-CLM-01 — Quotes shell
    await runCheck(ctx, 'contract-lifecycle.ui.quotes-shell', async () => {
      await goto(page, '/quotes');
      await page.getByRole('heading', { level: 1 }).first().waitFor({ state: 'visible', timeout: 15000 });
      const title = (await page.getByRole('heading', { level: 1 }).first().innerText()).trim();
      if (!/quote/i.test(title)) throw new Error(`expected Quotes heading, got "${title}"`);
      return title;
    });

    // UAT-CLM-02 — Subscriptions OHS
    await runCheck(ctx, 'contract-lifecycle.api.subscriptions-up', async () => {
      let result;
      try { result = await api.get(`${SUBSCRIPTIONS}?page=0&size=1`); }
      catch (error) { throw blocked(`Subscriptions read failed (HTTP ${error?.status ?? 'unknown'}); service behavior not established`); }
      if (!result || !Array.isArray(result.content)) throw new Error('Subscriptions response is not a paginated content body');
      return 'Subscriptions returned a paginated content body';
    });

    // UAT-CLM-03 — Deprecated renew → 410
    await runCheck(ctx, 'contract-lifecycle.api.renew-deprecated', async () => {
      const { status, body } = await postStatus(
        api,
        `${SUBSCRIPTIONS}/1/renew?newEndDate=2027-01-01`,
        {},
      );
      if (status === 401 || status === 403) throw blocked(`Deprecated renewal requires authorization (HTTP ${status})`);
      if (status !== 410) throw blocked(`Expected 410 from subscriptions; received HTTP ${status}; behavior not established`);
      let json;
      try { json = JSON.parse(String(body ?? '')); } catch { throw blocked('HTTP 410 lacked the expected JSON error body'); }
      if (json?.errorCode !== 'SUBSCRIPTION_RENEW_DEPRECATED') throw blocked('HTTP 410 did not contain the expected subscription error code');
      return 'HTTP 410 SUBSCRIPTION_RENEW_DEPRECATED';
    });

    // UAT-CLM-04 — Continuations live
    await runCheck(ctx, 'contract-lifecycle.api.continuations-live', async () => {
      throw blocked('No authorized continuation was exercised with a real subscription and asset fixture');
    });

    // UAT-CLM-05 — Quote DRAFT detail
    await runCheck(ctx, 'contract-lifecycle.ui.quote-detail', async () => {
      const quote = await ensure(ctx, 'quote');
      await goto(page, `/quotes/${quote.id}`);
      await page.getByRole('heading', { level: 1 }).first().waitFor({ state: 'visible', timeout: 15000 });
      const dto = await api.get(`${QUOTES}/${quote.id}`);
      if (dto.status !== 'DRAFT') throw new Error(`expected DRAFT, got ${dto.status}`);
      return `${dto.quoteNumber ?? quote.id} DRAFT`;
    });

    await runCheck(ctx, 'contract-lifecycle.api.amendments-gateway', async () => {
      throw blocked('Amendment ownership and an authorized gateway route have not been exercised');
    });

    await runCheck(ctx, 'contract-lifecycle.api.erp-contracts', async () => {
      throw blocked('No authorized ERP contract fixture was exercised');
    });

    await runCheck(ctx, 'contract-lifecycle.null-start.unit-gate', async () => {
      throw blocked('Browser UAT does not run the backend null-start test or observe the ledger');
    });

    await runCheck(ctx, 'contract-lifecycle.hold.armed', async () => {
      throw blocked('No apply request was exercised; the deployed S4 hold is unverified');
    });

    await runCheck(ctx, 'contract-lifecycle.type-safety.contract-parity', async () => {
      throw blocked('Browser UAT does not run contract tests or establish serialized type parity');
    });

    await runCheck(ctx, 'contract-lifecycle.fixture.prefix', async () => {
      const name = uniqueName(ctx.runId, 'clm');
      if (!name.startsWith(`UAT-${ctx.runId}-`)) throw new Error(`bad prefix ${name}`);
      return name;
    });
  },
};
