/**
 * Agentic UAT runner (Playwright) for Open CRM against live :4200 → gateway :8080.
 * Extends the smoke flows with catalog Signal Form validation checks.
 *
 *   cd frontend && node e2e/uat-agentic-run.mjs
 */
import { chromium } from 'playwright';

const BASE = process.env.UAT_BASE_URL || 'http://127.0.0.1:4200';
const stamp = Date.now();
const results = [];
const consoleErrors = [];

function ok(name, detail = '') {
  results.push({ name, status: 'PASS', detail });
  console.log(`PASS  ${name}${detail ? ` — ${detail}` : ''}`);
}
function fail(name, detail) {
  results.push({ name, status: 'FAIL', detail });
  console.error(`FAIL  ${name} — ${detail}`);
}

async function main() {
  const launchOpts = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (process.env.CHROMIUM_PATH) {
    launchOpts.executablePath = process.env.CHROMIUM_PATH;
  }

  const browser = await chromium.launch(launchOpts);
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  page.setDefaultTimeout(45000);
  page.on('pageerror', (e) => consoleErrors.push(`pageerror:${e.message}`));
  page.on('console', (m) => {
    if (m.type() === 'error') consoleErrors.push(`console:${m.text()}`);
  });

  async function gotoApp(path) {
    await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle').catch(() => {});
  }

  try {
    await gotoApp('/dashboard');
    await page.getByRole('heading', { name: 'Dashboard', exact: true }).waitFor();
    ok('dashboard loads');

    for (const [path, heading] of [
      ['/organizations', 'Organizations'],
      ['/contacts', 'Contacts'],
      ['/leads', 'Leads'],
      ['/deals', 'Deals'],
    ]) {
      await gotoApp(path);
      await page.getByRole('heading', { name: heading, exact: true }).waitFor();
      ok(`${path} list`);
    }

    await gotoApp('/organizations/new');
    const orgName = `UAT PW Org ${stamp}`;
    await page.getByRole('textbox', { name: 'Organization Name' }).fill(orgName);
    await page.getByRole('textbox', { name: 'City' }).fill('Austin');
    await page.getByRole('button', { name: 'Create' }).click();
    await page.waitForURL(/\/organizations(\/|$)/);
    await page.getByText(orgName).first().waitFor();
    ok('organization create', orgName);

    await gotoApp('/contacts/new');
    await page.getByRole('textbox', { name: 'First Name' }).fill('Pat');
    await page.getByRole('textbox', { name: 'Last Name' }).fill(`PW${stamp}`);
    await page.getByRole('textbox', { name: 'Email' }).fill(`pat.pw${stamp}@example.com`);
    await page.getByRole('button', { name: 'Create' }).click();
    await page.waitForURL(/\/contacts(\/|$)/);
    await page.getByText(`Pat PW${stamp}`).first().waitFor();
    ok('contact create');

    await gotoApp('/leads/new');
    await page.getByRole('textbox', { name: 'Title' }).fill(`UAT PW Lead ${stamp}`);
    await page.getByRole('spinbutton', { name: 'Estimated Value' }).fill('42000');
    await page.getByRole('button', { name: 'Create' }).click();
    await page.waitForURL(/\/leads(\/|$)/);
    await page.getByText(`UAT PW Lead ${stamp}`).first().waitFor();
    ok('lead create');

    await gotoApp('/deals/new');
    await page.getByRole('textbox', { name: 'Deal Name' }).fill(`UAT PW Deal ${stamp}`);
    await page.getByRole('spinbutton', { name: 'Amount ($)' }).fill('99000');
    await page.getByRole('button', { name: 'Create' }).click();
    await page.waitForURL(/\/deals(\/|$)/);
    await page.getByText(`UAT PW Deal ${stamp}`).first().waitFor();
    ok('deal create');

    for (const [path, heading] of [
      ['/catalog', 'Product Catalog'],
      ['/quotes', 'Quotes'],
      ['/approvals', /Approval/i],
      ['/price-books', /Price Book/i],
      ['/pricing-customers', /Pricing Customer/i],
      ['/rule-sets', /Rule Set/i],
      ['/product-bundles', /Bundle/i],
      ['/pricing/simulator', /Simulator|Pricing/i],
    ]) {
      await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded' });
      try {
        const headingOpt =
          heading instanceof RegExp
            ? { name: heading }
            : { name: heading, exact: true };
        await page.getByRole('heading', headingOpt).first().waitFor({ timeout: 10000 });
        ok(`${path} shell`);
      } catch (e) {
        fail(`${path} shell`, String(e.message || e));
      }
    }

    // Catalog create Signal Form validation (UAT-1)
    await gotoApp('/catalog');
    await page.getByRole('heading', { name: 'Product Catalog', exact: true }).waitFor();
    await page.getByRole('button', { name: /New Product/i }).first().click();
    await page.getByRole('dialog').waitFor();
    await page.getByLabel('SKU').click();
    await page.getByLabel('Name').click();
    await page.getByLabel('List price').fill('-1');
    await page.getByLabel('SKU').click();
    await page.waitForTimeout(300);

    if ((await page.getByText('SKU is required').count()) > 0) {
      ok('catalog validation SKU error');
    } else {
      fail('catalog validation SKU error', 'missing mat-error');
    }
    if ((await page.getByText('Name is required').count()) > 0) {
      ok('catalog validation Name error');
    } else {
      fail('catalog validation Name error', 'missing mat-error');
    }
    if ((await page.getByText('List price must be at least 0').count()) > 0) {
      ok('catalog validation listPrice min error');
    } else {
      fail(
        'catalog validation listPrice min error',
        'missing mat-error for min(0) — FIX-UAT1-CATALOG-LISTPRICE-MIN-NOT-SHOWN',
      );
    }
    await page.keyboard.press('Escape');

    await gotoApp('/quotes');
    const statusFilter = await page.locator('mat-select, [aria-label*="Status" i]').count();
    if (statusFilter > 0) {
      ok('quotes filters present');
    } else {
      fail('quotes filters present', 'status filter not found');
    }
  } catch (e) {
    fail('fatal', String(e.stack || e));
  } finally {
    await browser.close();
  }

  const failed = results.filter((r) => r.status === 'FAIL');
  console.log(`\n${results.length - failed.length}/${results.length} passed`);
  if (consoleErrors.length) {
    console.log(`Console/page errors (${consoleErrors.length}):`);
    for (const e of consoleErrors.slice(0, 25)) console.log(`  ${e}`);
  }
  process.exit(failed.length ? 1 : 0);
}

main();
