/**
 * Pricing Lifecycle Management UAT (UAT-PLM-01..07) against http://127.0.0.1:4200.
 *
 * Spec: docs/uat/pricing-lifecycle-management-cpq.md
 * Adapted from Aegis pricing-lifecycle-management.md onto open-crm-cpq price books,
 * rule sets, pricing simulator/execute, quotes, and renewal-uplift unit gates.
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, defect, gap } from '../../lib/health.mjs';
import { ensure, uniqueName } from '../fixtures.mjs';

const PRICE_BOOKS = '/api/v1/price-books';
const PRICING_CUSTOMERS = '/api/v1/pricing-customers';
const RULE_SETS = '/api/v1/rule-sets';
const PRICING_EXECUTE = '/api/v1/pricing/execute';
const PRICING_SIMULATE = '/api/v1/pricing/simulate';
const QUOTES = '/api/v1/quotes';
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
    const data = await api.post(path, body);
    return { status: 200, data };
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

/** Unique region — pricebook rejects duplicate region codes. */
function uniqueRegion(ctx) {
  const raw = `P${ctx.runId}`.replace(/[^A-Za-z0-9]/g, '').slice(0, 8).toUpperCase();
  return raw || `P${Date.now().toString(36).toUpperCase().slice(-6)}`;
}

export default {
  id: 'pricing-lifecycle',

  async run(ctx) {
    const { page, api, runId } = ctx;
    const region = uniqueRegion(ctx);
    const productExternalId = `PROD-PLM-${runId}`.slice(0, 40);
    const floorPrice = 600;
    const listPrice = 1000;

    // Shared fixtures for PLM path
    const customer = await ensure(ctx, 'pricingCustomer', {
      externalId: uniqueName(runId, 'plm-cust').slice(0, 64),
      name: uniqueName(runId, 'plm-customer'),
      segment: 'ENTERPRISE',
      region: 'NA',
    });
    const book = await ensure(ctx, 'priceBook', {
      name: uniqueName(runId, 'plm-book'),
      region,
      currency: 'USD',
      isDefault: false,
    });

    // UAT-PLM-01 — Floor entry (WTP analyzer = GAP documented in check notes via separate gap when N/A)
    await runCheck(ctx, 'pricing-lifecycle.api.floor-entry', async () => {
      const entry = await api.post(`${PRICE_BOOKS}/${book.id}/entries`, {
        productExternalId,
        listPrice,
        floorPrice,
        currency: 'USD',
      });
      api.track('priceBookEntry', entry.id, `${PRICE_BOOKS}/${book.id}/entries/${entry.id}`);
      if (Number(entry.floorPrice) !== floorPrice) {
        throw new Error(`expected floorPrice ${floorPrice}, got ${entry.floorPrice}`);
      }
      if (Number(entry.listPrice) !== listPrice) {
        throw new Error(`expected listPrice ${listPrice}, got ${entry.listPrice}`);
      }
      if (Number(entry.listPrice) < Number(entry.floorPrice)) {
        throw defect('listPrice below floorPrice — INV-PLM-FLOOR-01 breached');
      }
      return `entry ${entry.id} list=${entry.listPrice} floor=${entry.floorPrice}`;
    });

    await runCheck(ctx, 'pricing-lifecycle.gap.van-westendorp', async () => {
      throw gap(
        'Van Westendorp / WTP survey analyzer is Aegis-only; open-crm-cpq has no SURVEY-* API (UAT-PLM-01 WTP intersections)',
      );
    });

    // UAT-PLM-02 — Packaging shells
    await runCheck(ctx, 'pricing-lifecycle.ui.price-books', async () => {
      await goto(page, '/price-books');
      await page.getByRole('heading', { level: 1 }).first().waitFor({ state: 'visible', timeout: 15000 });
      const title = (await page.getByRole('heading', { level: 1 }).first().innerText()).trim();
      if (!/price\s*book/i.test(title)) throw new Error(`expected Price Books heading, got "${title}"`);
      return title;
    });

    await runCheck(ctx, 'pricing-lifecycle.ui.rule-sets', async () => {
      await goto(page, '/rule-sets');
      await page.getByRole('heading', { level: 1 }).first().waitFor({ state: 'visible', timeout: 15000 });
      const title = (await page.getByRole('heading', { level: 1 }).first().innerText()).trim();
      if (!/rule/i.test(title)) throw new Error(`expected Rule Sets heading, got "${title}"`);
      const { status } = await getStatus(api, `${RULE_SETS}?page=0&size=1`);
      assertLive('rule-sets', status);
      return `${title} + API ${status}`;
    });

    await runCheck(ctx, 'pricing-lifecycle.gap.tier-fence', async () => {
      throw gap(
        'TierFenceValidator / anti-cannibalization write-gate is Aegis-only; no ERR_PLM_TIER_CANNIBALIZATION in open-crm-cpq',
      );
    });

    // UAT-PLM-03 — Simulator UI + execute
    await runCheck(ctx, 'pricing-lifecycle.ui.simulator', async () => {
      await goto(page, '/pricing/simulator');
      await page.getByRole('heading', { level: 1 }).first().waitFor({ state: 'visible', timeout: 15000 });
      const title = (await page.getByRole('heading', { level: 1 }).first().innerText()).trim();
      if (!/simulat/i.test(title)) throw new Error(`expected Pricing Simulator heading, got "${title}"`);
      return title;
    });

    let executeResult;
    await runCheck(ctx, 'pricing-lifecycle.api.execute', async () => {
      const { status, data, body, message } = await postStatus(api, PRICING_EXECUTE, {
        customerExternalId: customer.externalId,
        lineItems: [{ productExternalId, quantity: 2 }],
        region,
        currency: 'USD',
        durationInMonths: 12,
        subscriptionType: 'NEW',
      });
      if (status !== 200) {
        throw new Error(`execute expected 200, got ${status}: ${message ?? body}`);
      }
      if (!data?.success) throw new Error(`PricingResult.success !== true: ${JSON.stringify(data).slice(0, 200)}`);
      if (!data.waterfall) throw new Error('PricingResult missing waterfall');
      if (!data.financialMetrics) throw new Error('PricingResult missing financialMetrics');
      executeResult = data;
      return `execute OK net=${data.waterfall.netPrice} arr=${data.financialMetrics.arr}`;
    });

    await runCheck(ctx, 'pricing-lifecycle.api.simulate', async () => {
      const { status, data, body, message } = await postStatus(api, PRICING_SIMULATE, {
        customerExternalId: customer.externalId,
        lineItems: [{ productExternalId, quantity: 2 }],
        region,
        currency: 'USD',
        durationInMonths: 12,
        subscriptionType: 'NEW',
      });
      if (status !== 200) {
        throw new Error(`simulate expected 200, got ${status}: ${message ?? body}`);
      }
      if (!data?.success) throw new Error(`simulate PricingResult.success !== true`);
      return `simulate OK net=${data.waterfall?.netPrice}`;
    });

    await runCheck(ctx, 'pricing-lifecycle.gap.seeded-portfolio-sim', async () => {
      throw gap(
        'PlmStrategySimulator seeded 24-month cohort / SimulationWorldHash is Aegis-only (UAT-PLM-03 NRR guardrails)',
      );
    });

    // UAT-PLM-04 — Q2C
    await runCheck(ctx, 'pricing-lifecycle.ui.quotes', async () => {
      await goto(page, '/quotes');
      await page.getByRole('heading', { level: 1 }).first().waitFor({ state: 'visible', timeout: 15000 });
      const quote = await ensure(ctx, 'quote', {
        name: uniqueName(runId, 'plm-quote'),
        customerExternalId: customer.externalId,
        currency: 'USD',
      });
      await goto(page, `/quotes/${quote.id}`);
      await page.getByRole('heading', { level: 1 }).first().waitFor({ state: 'visible', timeout: 15000 });
      const dto = await api.get(`${QUOTES}/${quote.id}`);
      if (dto.status !== 'DRAFT') throw new Error(`expected DRAFT, got ${dto.status}`);
      return `${dto.quoteNumber ?? quote.id} DRAFT`;
    });

    await runCheck(ctx, 'pricing-lifecycle.api.erp-contracts', async () => {
      const { status, body, message } = await getStatus(api, `${ERP_CONTRACTS}/uat-plm`);
      if (status === 501) {
        throw gap(`erp-adapter stub HTTP 501 (${String(body ?? message).slice(0, 120)})`);
      }
      if (status === 404) {
        throw gap('ERP contracts route not found through gateway');
      }
      assertLive('erp-contracts', status);
      return `erp-contracts HTTP ${status}`;
    });

    // UAT-PLM-05 — Waterfall conservation
    await runCheck(ctx, 'pricing-lifecycle.api.waterfall-conservation', async () => {
      if (!executeResult) throw new Error('executeResult missing — execute check must run first');
      const lines = executeResult.lineResults ?? [];
      const sumFinal = lines.reduce((acc, line) => acc + Number(line.finalPrice ?? 0), 0);
      const net = Number(executeResult.waterfall?.netPrice ?? NaN);
      if (!Number.isFinite(net)) throw new Error('waterfall.netPrice missing');
      if (Math.abs(sumFinal - net) > 0.001) {
        throw defect(`waterfall imbalance: Σ finalPrice=${sumFinal} netPrice=${net}`);
      }
      const types = (executeResult.waterfall.steps ?? []).map((s) => s.stepType);
      if (!types.includes('LIST_PRICE') || !types.includes('NET_PRICE')) {
        throw new Error(`waterfall steps missing LIST_PRICE/NET_PRICE: ${types.join(',')}`);
      }
      return `conserved net=${net} steps=${types.join(',')}`;
    });

    await runCheck(ctx, 'pricing-lifecycle.gap.pocket-auditor', async () => {
      throw gap(
        'PocketMarginWaterfallAuditor / ALERT_PLM_POCKET_MARGIN_BREACH is Aegis-only; open-crm-cpq exposes execute waterfall only',
      );
    });

    // UAT-PLM-06 — Sunset / uplift
    await runCheck(ctx, 'pricing-lifecycle.api.ruleset-deprecate', async () => {
      const rs = await ensure(ctx, 'ruleSet', {
        name: uniqueName(runId, 'plm-rs'),
        description: 'PLM sunset fixture',
        engineType: 'DROOLS',
      });
      // DRAFT cannot deprecate — expect business 4xx; endpoint must be live (not 503/404 gateway miss)
      const { status, body, message } = await postStatus(api, `${RULE_SETS}/${rs.id}/deprecate`, {});
      assertLive('rule-sets deprecate', status);
      if (status === 404) throw defect('rule-sets deprecate route missing on gateway');
      // ACTIVE→DEPRECATED is the happy path; DRAFT→deprecate is IllegalState → typically 400/409/422
      if (status === 200) return `deprecated rule-set ${rs.id}`;
      if (status >= 400 && status < 500) {
        return `deprecate reachable HTTP ${status} (${String(body ?? message).slice(0, 80)})`;
      }
      throw new Error(`unexpected deprecate status ${status}`);
    });

    await runCheck(ctx, 'pricing-lifecycle.uplift.unit-gate', async () => {
      const { readFileSync, existsSync } = await import('node:fs');
      const path =
        '/workspace/backend/services/pricing-service/src/main/java/com/opencrm/pricing/renewal/RenewalUpliftResolver.java';
      const testPath =
        '/workspace/backend/services/pricing-service/src/test/java/com/opencrm/pricing/renewal/RenewalUpliftResolverTest.java';
      if (!existsSync(path)) throw defect('RenewalUpliftResolver.java missing');
      if (!existsSync(testPath)) throw defect('RenewalUpliftResolverTest.java missing — INV-PLM-MIG-01 gate absent');
      const src = readFileSync(path, 'utf8');
      if (!src.includes('maxIncreasePct') && !src.includes('maxIncrease')) {
        throw defect('RenewalUpliftResolver does not reference maxIncrease uplift cap');
      }
      return 'RenewalUpliftResolver + unit test present (uplift cap TDD gate)';
    });

    await runCheck(ctx, 'pricing-lifecycle.gap.grandfathering', async () => {
      throw gap(
        'Legacy cohort grandfathering / Migration Price Protection Credit is Aegis-only (UAT-PLM-06)',
      );
    });

    // Soft-delete draft book (sunset proxy for price books)
    await runCheck(ctx, 'pricing-lifecycle.api.pricebook-soft-delete', async () => {
      await api.del(`${PRICE_BOOKS}/${book.id}`);
      api.untrack('priceBook', book.id);
      const { status } = await getStatus(api, `${PRICE_BOOKS}/${book.id}`);
      if (status !== 404) throw new Error(`expected 404 after soft-delete, got ${status}`);
      return `price book ${book.id} soft-deleted → 404`;
    });

    // UAT-PLM-07 — Type safety
    await runCheck(ctx, 'pricing-lifecycle.type-safety.contract-parity', async () => {
      const { readFileSync, existsSync } = await import('node:fs');
      const model = '/workspace/frontend/libs/shared/models/src/lib/pricing.model.ts';
      const parity = '/workspace/frontend/libs/shared/models/src/lib/backend-fe-contract-parity.spec.ts';
      for (const path of [model, parity]) {
        if (!existsSync(path)) throw defect(`type-safety artifact missing: ${path}`);
      }
      const modelSrc = readFileSync(model, 'utf8');
      for (const token of [
        'export interface PriceBookDto',
        'export interface PriceBookEntryDto',
        'export interface PricingCustomerDto',
        'export interface RuleSetDto',
        'export interface PricingResult',
        'export interface FinancialMetrics',
        'exitArr',
      ]) {
        if (!modelSrc.includes(token)) {
          throw defect(`pricing.model.ts missing ${token}`);
        }
      }
      const paritySrc = readFileSync(parity, 'utf8');
      if (!paritySrc.includes('Backend ↔ frontend contract parity (PLM surfaces)')) {
        throw defect('backend-fe-contract-parity.spec.ts missing PLM surfaces suite');
      }
      return 'PLM FE models + parity suite present (PriceBook/Entry/Customer/RuleSet/PricingResult/exitArr)';
    });

    await runCheck(ctx, 'pricing-lifecycle.api.customers-up', async () => {
      const { status } = await getStatus(api, `${PRICING_CUSTOMERS}?page=0&size=1`);
      if (status !== 200 && status !== 401 && status !== 403) {
        throw new Error(`unexpected pricing-customers status ${status}`);
      }
      return assertLive('pricing-customers', status);
    });

    await runCheck(ctx, 'pricing-lifecycle.fixture.prefix', async () => {
      const name = uniqueName(ctx.runId, 'plm');
      if (!name.startsWith(`UAT-${ctx.runId}-`)) throw new Error(`bad prefix ${name}`);
      return name;
    });
  },
};
