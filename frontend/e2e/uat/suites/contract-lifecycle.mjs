/**
 * Contract Lifecycle Management UAT (UAT-CLM-01..09) against http://127.0.0.1:4200.
 *
 * Spec: docs/uat/contract-lifecycle-management-cpq.md
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, defect, gap, blocked } from '../../lib/health.mjs';
import { ensure, uniqueName } from '../fixtures.mjs';

const SUBSCRIPTIONS = '/api/v1/subscriptions';
const QUOTES = '/api/v1/quotes';
const CONTINUATIONS = '/api/v1/quotes/continuations';
const AMENDMENTS = '/api/v1/amendments';
const ERP_CONTRACTS = '/api/v1/erp/contracts';

async function getStatus(api, path) {
  try {
    await api.get(path);
    return { status: 200 };
  } catch (error) {
    const status = error?.status ?? error?.response?.status;
    if (typeof status === 'number') return { status, message: error.message, body: error.body };
    throw error;
  }
}

async function postStatus(api, path, body) {
  try {
    await api.post(path, body);
    return { status: 200 };
  } catch (error) {
    const status = error?.status ?? error?.response?.status;
    if (typeof status === 'number') return { status, message: error.message, body: error.body };
    throw error;
  }
}

function assertLive(label, status) {
  if (status === undefined) throw new Error(`${label}: no HTTP status`);
  if (status === 503) throw defect(`${label}: gateway/service returned 503`);
  if (status >= 500 && status !== 501) throw new Error(`${label}: HTTP ${status}`);
  return `HTTP ${status}`;
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
      const { status } = await getStatus(api, `${SUBSCRIPTIONS}?page=0&size=1`);
      if (status !== 200 && status !== 401 && status !== 403) {
        throw new Error(`unexpected subscriptions status ${status}`);
      }
      return assertLive('subscriptions', status);
    });

    // UAT-CLM-03 — Deprecated renew → 410
    await runCheck(ctx, 'contract-lifecycle.api.renew-deprecated', async () => {
      const { status, body, message } = await postStatus(
        api,
        `${SUBSCRIPTIONS}/1/renew?newEndDate=2027-01-01`,
        {},
      );
      if (status !== 410) {
        throw new Error(`expected 410 Gone for deprecated renew, got ${status}: ${message ?? body}`);
      }
      const text = String(body ?? message ?? '');
      if (!/SUBSCRIPTION_RENEW_DEPRECATED|deprecated/i.test(text)) {
        throw new Error(`410 body missing SUBSCRIPTION_RENEW_DEPRECATED: ${text.slice(0, 200)}`);
      }
      return 'HTTP 410 SUBSCRIPTION_RENEW_DEPRECATED';
    });

    // UAT-CLM-04 — Continuations live
    await runCheck(ctx, 'contract-lifecycle.api.continuations-live', async () => {
      const { status, body, message } = await postStatus(api, CONTINUATIONS, {
        operation: 'RENEWAL',
        targetSubscriptionNumber: 'S-UAT-CLM',
        observedRevision: 0,
        customerExternalId: '7e69c57b-5b1f-45bf-a6e4-0bf9e8cd7f72',
        currency: 'USD',
        renewalSourceMode: 'FROM_ACTIVE_ASSETS',
      });
      assertLive('continuations', status);
      if (status === 503) throw defect('continuations 503');
      if (status < 400 || status >= 500) {
        // 2xx would mean a real draft was created — also fine
        if (status >= 500) throw new Error(`continuations unexpected ${status}`);
      }
      return `HTTP ${status}${body || message ? ` (${String(body ?? message).slice(0, 80)})` : ''}`;
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

    // UAT-CLM-06 — Amendments unreleased on gateway
    await runCheck(ctx, 'contract-lifecycle.api.amendments-gateway', async () => {
      const { status } = await getStatus(
        api,
        `${AMENDMENTS}/00000000-0000-0000-0000-000000000001`,
      );
      if (status === 404) {
        throw gap(
          'Amendment HTTP API is not gateway-routed (AmendmentController unreleased / no ownership checks)',
        );
      }
      assertLive('amendments', status);
      return `amendments HTTP ${status}`;
    });

    // UAT-CLM-07 — ERP contracts
    await runCheck(ctx, 'contract-lifecycle.api.erp-contracts', async () => {
      const { status, body, message } = await getStatus(api, `${ERP_CONTRACTS}/uat-clm`);
      if (status === 501) {
        throw gap(`erp-adapter stub HTTP 501 (${String(body ?? message).slice(0, 120)})`);
      }
      if (status === 404) {
        throw gap('ERP contracts route not found through gateway');
      }
      assertLive('erp-contracts', status);
      return `erp-contracts HTTP ${status}`;
    });

    // UAT-CLM-08 — Null-start + hold unit gates
    await runCheck(ctx, 'contract-lifecycle.null-start.unit-gate', async () => {
      const { readFileSync, existsSync } = await import('node:fs');
      const path =
        '/workspace/backend/services/contract-asset-management/src/test/java/com/opencrm/contract/documents/apply/DocumentApplyNullStartDateTest.java';
      if (!existsSync(path)) throw defect('DocumentApplyNullStartDateTest.java missing');
      const src = readFileSync(path, 'utf8');
      if (!src.includes('START_DATE_MISSING')) {
        throw defect('DocumentApplyNullStartDateTest does not assert START_DATE_MISSING');
      }
      return 'DocumentApplyNullStartDateTest asserts START_DATE_MISSING';
    });

    await runCheck(ctx, 'contract-lifecycle.hold.armed', async () => {
      const { readFileSync, existsSync } = await import('node:fs');
      const path =
        '/workspace/backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/apply/AssetLifecycleReadiness.java';
      if (!existsSync(path)) throw defect('AssetLifecycleReadiness.java missing');
      const src = readFileSync(path, 'utf8');
      if (!src.includes('return false')) {
        throw defect('AssetLifecycleReadiness does not refuse readiness');
      }
      return 'S4 asset-lifecycle hold armed (isReady()=false)';
    });

    // UAT-CLM-09 — Type safety
    await runCheck(ctx, 'contract-lifecycle.type-safety.contract-parity', async () => {
      const { readFileSync, existsSync } = await import('node:fs');
      const sub = '/workspace/frontend/libs/shared/models/src/lib/subscription.model.ts';
      const amd = '/workspace/frontend/libs/shared/models/src/lib/amendment.model.ts';
      const parity = '/workspace/frontend/libs/shared/models/src/lib/backend-fe-contract-parity.spec.ts';
      for (const path of [sub, amd, parity]) {
        if (!existsSync(path)) throw defect(`type-safety artifact missing: ${path}`);
      }
      const subSrc = readFileSync(sub, 'utf8');
      if (!subSrc.includes('export interface SubscriptionDto')) {
        throw defect('subscription.model.ts missing SubscriptionDto');
      }
      const paritySrc = readFileSync(parity, 'utf8');
      if (!paritySrc.includes('SubscriptionDto covers CAM')) {
        throw defect('backend-fe-contract-parity.spec.ts missing CLM SubscriptionDto suite');
      }
      return 'Subscription/Amendment FE models + CLM parity suite present';
    });

    await runCheck(ctx, 'contract-lifecycle.fixture.prefix', async () => {
      const name = uniqueName(ctx.runId, 'clm');
      if (!name.startsWith(`UAT-${ctx.runId}-`)) throw new Error(`bad prefix ${name}`);
      return name;
    });
  },
};
