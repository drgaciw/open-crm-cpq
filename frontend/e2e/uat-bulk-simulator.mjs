#!/usr/bin/env node
/**
 * BC-03-10A browser UAT — Pricing Simulator Bulk tab.
 * Requires CRM UI :4200, gateway :8080, pricing-service :8082.
 *
 * Usage: node e2e/uat-bulk-simulator.mjs
 */
import { chromium } from 'playwright';

const BASE = process.env.CRM_BASE_URL || 'http://localhost:4200';

const TWO_OK = [
  {
    customerExternalId: 'UAT-SIM-CUST-NA',
    quoteExternalId: 'Q-UAT-B1',
    region: 'NA',
    currency: 'USD',
    durationInMonths: 12,
    subscriptionType: 'NEW',
    lineItems: [
      {
        productExternalId: 'PROD-SIM-CORE',
        lineItemType: 'SUBSCRIPTION',
        quantity: 5,
      },
    ],
  },
  {
    customerExternalId: 'UAT-SIM-CUST-EMEA',
    quoteExternalId: 'Q-UAT-B2',
    region: 'EMEA',
    currency: 'EUR',
    durationInMonths: 12,
    subscriptionType: 'AMENDMENT',
    lineItems: [
      {
        productExternalId: 'PROD-SIM-CORE',
        lineItemType: 'SUBSCRIPTION',
        quantity: 2,
      },
    ],
  },
];

const PARTIAL = [
  {
    customerExternalId: 'UAT-SIM-CUST-NA',
    quoteExternalId: 'Q-UAT-OK',
    region: 'NA',
    currency: 'USD',
    durationInMonths: 12,
    subscriptionType: 'NEW',
    lineItems: [
      {
        productExternalId: 'PROD-SIM-CORE',
        lineItemType: 'SUBSCRIPTION',
        quantity: 5,
      },
    ],
  },
  {
    customerExternalId: 'NO-SUCH-CUST',
    quoteExternalId: 'Q-UAT-BAD',
    region: 'NA',
    currency: 'USD',
    durationInMonths: 12,
    subscriptionType: 'NEW',
    lineItems: [
      {
        productExternalId: 'PROD-SIM-CORE',
        lineItemType: 'SUBSCRIPTION',
        quantity: 1,
      },
    ],
  },
];

async function pasteAndSimulate(page, contexts, expectSummarySubstring) {
  await page.evaluate((json) => {
    const ta = document.querySelector('[data-testid="bulk-json-input"]');
    if (!ta) throw new Error('bulk-json-input missing');
    ta.value = json;
    ta.dispatchEvent(new Event('input', { bubbles: true }));
  }, JSON.stringify(contexts));
  const responsePromise = page.waitForResponse(
    (res) =>
      res.url().includes('/api/v1/pricing/simulate/bulk') && res.request().method() === 'POST',
    { timeout: 20000 },
  );
  await page.getByTestId('bulk-simulate').click();
  const response = await responsePromise;
  if (!response.ok()) {
    throw new Error(`simulate/bulk HTTP ${response.status()}`);
  }
  await page.getByTestId('bulk-summary').waitFor({ state: 'visible', timeout: 20000 });
  if (expectSummarySubstring) {
    await page
      .getByTestId('bulk-summary')
      .getByText(expectSummarySubstring, { exact: false })
      .waitFor({ timeout: 10000 });
  }
}

async function readSummary(page) {
  const text = (await page.getByTestId('bulk-summary').textContent()) || '';
  const num = (label) => {
    const m = text.match(new RegExp(`${label}:\\s*(\\d+)`, 'i'));
    return m ? Number(m[1]) : null;
  };
  return {
    text,
    total: num('Total requests'),
    successful: num('Successful requests'),
    failed: num('Failed requests'),
  };
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  const results = [];

  try {
    await page.goto(`${BASE}/pricing/simulator`, { waitUntil: 'networkidle' });
    await page.getByRole('heading', { name: 'Pricing Simulator' }).waitFor();
    await page.getByRole('tab', { name: 'Bulk' }).click();
    await page.getByTestId('bulk-simulator').waitFor();

    // Scenario 1: two successful contexts
    await pasteAndSimulate(page, TWO_OK, 'Successful requests: 2');
    const okSummary = await readSummary(page);
    const okTable = (await page.getByTestId('data-table').textContent()) || '';
    const okPass =
      okSummary.total === 2 &&
      okSummary.successful === 2 &&
      okSummary.failed === 0 &&
      okTable.includes('Q-UAT-B1') &&
      okTable.includes('Q-UAT-B2') &&
      /\bSuccess\b/.test(okTable);
    results.push({
      id: 'bulk-two-ok',
      pass: okPass,
      detail: okSummary,
    });

    // Scenario 2: partial failure keeps sibling success
    await pasteAndSimulate(page, PARTIAL, 'Failed requests: 1');
    const partSummary = await readSummary(page);
    const partTable = (await page.getByTestId('data-table').textContent()) || '';
    const partPass =
      partSummary.total === 2 &&
      partSummary.successful === 1 &&
      partSummary.failed === 1 &&
      partTable.includes('Q-UAT-OK') &&
      partTable.includes('Q-UAT-BAD') &&
      /\bSuccess\b/.test(partTable) &&
      /\bFailed\b/.test(partTable) &&
      /RESOURCE_NOT_FOUND|NO-SUCH-CUST/.test(partTable);
    results.push({
      id: 'bulk-partial-fail',
      pass: partPass,
      detail: partSummary,
    });
  } finally {
    await browser.close();
  }

  for (const r of results) {
    console.log(`${r.pass ? 'PASS' : 'FAIL'} ${r.id}`, JSON.stringify(r.detail));
  }
  const failed = results.filter((r) => !r.pass);
  if (failed.length) {
    process.exitCode = 1;
  }
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
