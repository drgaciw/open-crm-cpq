/**
 * Check seeded Account and Opportunity detail layouts in Chromium.
 * Run against a disposable stack with UAT_BASE_URL set to its frontend:
 *   node --test e2e/uat-mobile-detail.mjs
 * UAT_FIXTURE_BASE_URL may point to the same backend through its original dev origin.
 */
import assert from 'node:assert/strict';
import test from 'node:test';
import { createApi } from './lib/api.mjs';
import { BASE, launch } from './lib/browser.mjs';
import { ensure, newRunId } from './uat/fixtures.mjs';

test('Account and Opportunity details fit mobile and retain desktop columns', async (t) => {
  const { browser, page } = await launch({ timeoutMs: 10_000 });
  const api = createApi(page.request, { base: process.env.UAT_FIXTURE_BASE_URL || BASE });
  const fixtures = { api, runId: newRunId() };
  t.after(async () => {
    try {
      assert.deepEqual(await api.teardown(), [], 'Run-owned fixture cleanup');
    } finally {
      await browser.close();
    }
  });

  const account = await ensure(fixtures, 'organization');
  const contact = await ensure(fixtures, 'contact', { organizationId: account.id });
  const opportunity = await ensure(fixtures, 'deal', {
    organizationId: account.id,
    primaryContactId: contact.id,
    currency: 'USD',
  });
  const routes = [
    { label: 'Account', path: `/organizations/${account.id}`, name: account.name, component: 'app-organization-detail' },
    { label: 'Opportunity', path: `/deals/${opportunity.id}`, name: opportunity.name, component: 'app-deal-detail' },
  ];

  for (const route of routes) {
    for (const width of [390, 1440]) {
      await t.test(`${route.label} detail at ${width}px`, async () => {
        await page.setViewportSize({ width, height: 844 });
        const response = await page.goto(`${BASE}${route.path}`, { waitUntil: 'domcontentloaded' });
        assert.equal(response?.status(), 200);
        const detail = page.locator(`main ${route.component}`);
        await detail.getByRole('heading', { name: route.name, exact: true }).waitFor();
        await detail.getByRole('button', { name: /^(edit\s+)?edit$/i }).waitFor();

        const shell = page.locator('mat-sidenav-content');
        const widths = await shell.evaluate((content) => ({
          viewport: window.innerWidth,
          documentClient: document.documentElement.clientWidth,
          documentScroll: document.documentElement.scrollWidth,
          contentClient: content.clientWidth,
          contentScroll: content.scrollWidth,
        }));
        t.diagnostic(`${route.label} at ${width}px ${JSON.stringify(widths)}`);
        assert.equal(widths.viewport, width);
        assert.equal(widths.documentScroll, widths.documentClient, `${route.label} document overflows`);
        assert.equal(widths.contentScroll, widths.contentClient, `${route.label} detail overflows`);

        if (width === 1440) {
          const cards = detail.locator('.detail-grid > mat-card');
          const first = await cards.nth(0).boundingBox();
          const second = await cards.nth(1).boundingBox();
          assert.ok(first && second && first.y === second.y && second.x >= first.x + first.width,
            `${route.label} desktop cards should be side by side`);
        }
      });
    }
  }
});
