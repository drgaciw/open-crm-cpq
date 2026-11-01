/**
 * Lightweight Playwright UAT smoke for Open CRM (no Nx e2e project yet).
 * Prerequisites: frontend :4200, CRM API reachable via /api proxy.
 *
 *   cd frontend && npx playwright install chromium   # once; playwright is a devDependency
 *   cd frontend && npm run e2e:smoke
 */
import { launch, BASE } from './lib/browser.mjs';

const stamp = Date.now();
const results = [];

function ok(name, detail = '') {
  results.push({ name, status: 'PASS', detail });
  console.log(`PASS  ${name}${detail ? ` — ${detail}` : ''}`);
}
function fail(name, detail) {
  results.push({ name, status: 'FAIL', detail });
  console.error(`FAIL  ${name} — ${detail}`);
}

async function main() {
  const { browser, page } = await launch({ timeoutMs: 45000 });

  async function gotoApp(path) {
    await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle').catch(() => {});
  }

  try {
    await gotoApp('/dashboard');
    // Page titles are h1; empty-state copy is often an h3 that substring-matches the entity name
    // (e.g. "No organizations yet" vs "Organizations") — require exact accessible name.
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

    // Organization create
    await gotoApp('/organizations/new');
    const orgName = `UAT PW Org ${stamp}`;
    await page.getByRole('textbox', { name: 'Organization Name' }).fill(orgName);
    await page.getByRole('textbox', { name: 'City' }).fill('Austin');
    await page.getByRole('button', { name: 'Create' }).click();
    await page.waitForURL(/\/organizations(\/|$)/);
    await page.getByText(orgName).first().waitFor();
    ok('organization create', orgName);

    // Contact create
    await gotoApp('/contacts/new');
    await page.getByRole('textbox', { name: 'First Name' }).fill('Pat');
    await page.getByRole('textbox', { name: 'Last Name' }).fill(`PW${stamp}`);
    await page.getByRole('textbox', { name: 'Email' }).fill(`pat.pw${stamp}@example.com`);
    await page.getByRole('button', { name: 'Create' }).click();
    await page.waitForURL(/\/contacts(\/|$)/);
    await page.getByText(`Pat PW${stamp}`).first().waitFor();
    ok('contact create');

    // Lead create
    await gotoApp('/leads/new');
    await page.getByRole('textbox', { name: 'Title' }).fill(`UAT PW Lead ${stamp}`);
    await page.getByRole('spinbutton', { name: 'Estimated Value' }).fill('42000');
    await page.getByRole('button', { name: 'Create' }).click();
    await page.waitForURL(/\/leads(\/|$)/);
    await page.getByText(`UAT PW Lead ${stamp}`).first().waitFor();
    ok('lead create');

    // Deal create
    await gotoApp('/deals/new');
    await page.getByRole('textbox', { name: 'Deal Name' }).fill(`UAT PW Deal ${stamp}`);
    await page.getByRole('spinbutton', { name: 'Amount ($)' }).fill('99000');
    await page.getByRole('button', { name: 'Create' }).click();
    await page.waitForURL(/\/deals(\/|$)/);
    await page.getByText(`UAT PW Deal ${stamp}`).first().waitFor();
    ok('deal create');

    // CPQ pages render (backends may be down — assert shell only)
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
        await page.getByRole('heading', { name: heading, exact: typeof heading === 'string' }).first().waitFor({ timeout: 10000 });
        ok(`${path} shell`);
      } catch (e) {
        fail(`${path} shell`, String(e.message || e));
      }
    }
  } catch (e) {
    fail('fatal', String(e.stack || e));
  } finally {
    await browser.close();
  }

  const failed = results.filter((r) => r.status === 'FAIL');
  console.log(`\n${results.length - failed.length}/${results.length} passed`);
  process.exit(failed.length ? 1 : 0);
}

main();
