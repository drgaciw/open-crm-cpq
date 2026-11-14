/**
 * Lightweight Playwright UAT smoke for Open CRM (no Nx e2e project yet).
 * Prerequisites: frontend :4200, CRM API reachable via /api proxy.
 *
 *   cd frontend && npx playwright install chromium   # once; playwright is a devDependency
 *   cd frontend && npm run e2e:smoke
 *
 * UI-CRM-SLDS Phase 2/3 additions below:
 *   - Contacts/Accounts/Leads/Deals lists assert the shared `app-data-table`
 *     (`[data-testid="data-table"]`) rather than any raw mat-table markup.
 *   - Lead detail: the agent panel (`[data-testid="agent-panel"]`) is always present (state is
 *     'empty' or 'ready' depending on whether a score advisory exists) and its collapse/expand
 *     toggle works.
 *   - Deal detail: the agent panel is present but 'disabled' and explicitly labelled — deal
 *     probability advisory is out of scope for Jev (ADR-015); runtime flags stay off.
 *   - Quote DRAFT detail: additive, best-effort. quote-service is frequently down during CRM-only
 *     UAT (empty list / non-2xx is expected then) — that is a SKIP, never a FAIL.
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
function skip(name, detail) {
  results.push({ name, status: 'SKIP', detail });
  console.log(`SKIP  ${name}${detail ? ` — ${detail}` : ''}`);
}

/** Assert the shared DataTableComponent (not a raw mat-table) renders the list. */
async function expectDataTable(page, name) {
  try {
    await page.getByTestId('data-table').waitFor({ state: 'visible', timeout: 8000 });
    ok(name);
  } catch (e) {
    fail(name, String(e.message || e));
  }
}

/**
 * Assert the agent panel is present and its collapse/expand toggle works, regardless of its
 * advisory state (empty/ready/disabled/...). Works on any page that renders <app-agent-panel>.
 */
async function expectAgentPanelCollapsible(page, name) {
  try {
    const panel = page.getByTestId('agent-panel');
    await panel.waitFor({ state: 'visible', timeout: 8000 });
    const toggle = panel.getByRole('button', { name: /Collapse agent panel|Expand agent panel/ });
    const initiallyExpanded = (await toggle.getAttribute('aria-expanded')) === 'true';
    await toggle.click();
    await page.waitForTimeout(150); // no animation on the panel body; settle any CD
    const afterToggle = (await toggle.getAttribute('aria-expanded')) === 'true';
    if (afterToggle === initiallyExpanded) {
      throw new Error(`aria-expanded did not change on click (stayed ${initiallyExpanded})`);
    }
    await toggle.click(); // restore
    await page.waitForTimeout(150);
    const restored = (await toggle.getAttribute('aria-expanded')) === 'true';
    if (restored !== initiallyExpanded) {
      throw new Error(`toggling twice did not restore aria-expanded=${initiallyExpanded}`);
    }
    ok(name, `collapse/expand toggles aria-expanded (started ${initiallyExpanded})`);
  } catch (e) {
    fail(name, String(e.message || e));
  }
}

async function main() {
  const { browser, page } = await launch({ timeoutMs: 45000 });

  async function gotoApp(path) {
    await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle').catch(() => {});
  }

  /** Search a list page by query and wait for the search response before asserting. */
  async function searchList(path, apiPath, placeholder, query) {
    await gotoApp(path);
    const responded = page
      .waitForResponse((r) => r.url().includes(`${apiPath}/search`) && r.ok(), { timeout: 10000 })
      .catch(() => null);
    await page.getByRole('textbox', { name: placeholder, exact: true }).fill(query);
    await responded;
    await page.waitForLoadState('networkidle').catch(() => {});
  }

  try {
    await gotoApp('/dashboard');
    // Page titles are h1; empty-state copy is often an h3 that substring-matches the entity name
    // (e.g. "No organizations yet" vs "Organizations") — require exact accessible name.
    await page.getByRole('heading', { name: 'Dashboard', exact: true }).waitFor();
    ok('dashboard loads');

    for (const [path, heading] of [
      ['/organizations', 'Accounts'],
      ['/contacts', 'Contacts'],
      ['/leads', 'Leads'],
      // The /deals route's page title is "Opportunities" (sidebar label, search placeholder and
      // docs/design/UI-CRM-SLDS-design-contract.md all agree — "Opportunities (deals)"). A prior
      // version of this script asserted "Deals" here, which no longer matches the rendered h1;
      // fixed rather than carried forward as a false failure.
      ['/deals', 'Opportunities'],
    ]) {
      await gotoApp(path);
      await page.getByRole('heading', { name: heading, exact: true }).waitFor();
      ok(`${path} list`);
    }

    // Organization create
    await gotoApp('/organizations/new');
    const orgName = `UAT PW Org ${stamp}`;
    await page.getByRole('textbox', { name: 'Account Name' }).fill(orgName);
    await page.getByRole('textbox', { name: 'City' }).fill('Austin');
    await page.getByRole('button', { name: 'Create' }).click();
    await page.waitForURL(/\/organizations(\/|$)/);
    await page.getByText(orgName).first().waitFor();
    ok('organization create', orgName);

    // Accounts list: shared DataTable renders the row for the record just created.
    await searchList('/organizations', '/api/v1/organizations', 'Search accounts...', orgName);
    await expectDataTable(page, 'organizations list shows app-data-table');

    // Contact create
    await gotoApp('/contacts/new');
    await page.getByRole('textbox', { name: 'First Name' }).fill('Pat');
    await page.getByRole('textbox', { name: 'Last Name' }).fill(`PW${stamp}`);
    await page.getByRole('textbox', { name: 'Email' }).fill(`pat.pw${stamp}@example.com`);
    await page.getByRole('button', { name: 'Create' }).click();
    await page.waitForURL(/\/contacts(\/|$)/);
    await page.getByText(`Pat PW${stamp}`).first().waitFor();
    ok('contact create');

    await searchList('/contacts', '/api/v1/contacts', 'Search contacts...', `PW${stamp}`);
    await expectDataTable(page, 'contacts list shows app-data-table');

    // Lead create — lands on the lead detail page.
    await gotoApp('/leads/new');
    await page.getByRole('textbox', { name: 'Title' }).fill(`UAT PW Lead ${stamp}`);
    await page.getByRole('spinbutton', { name: 'Estimated Value' }).fill('42000');
    await page.getByRole('button', { name: 'Create' }).click();
    await page.waitForURL(/\/leads(\/|$)/);
    await page.getByText(`UAT PW Lead ${stamp}`).first().waitFor();
    ok('lead create');

    // Lead detail: the agent panel is always rendered (state 'empty' until Jev scores the lead,
    // 'ready' once it has) — never a silent absence — and its collapse/expand toggle works.
    await expectAgentPanelCollapsible(page, 'lead detail agent panel present + collapsible');

    await searchList('/leads', '/api/v1/leads', 'Search leads...', `UAT PW Lead ${stamp}`);
    await expectDataTable(page, 'leads list shows app-data-table');

    // Deal create — lands on the deal detail page.
    // Field labels are "Name" / "Amount" (not "Deal Name" / "Amount ($)" — deal-form.component.ts
    // was simplified since an earlier version of this script was written; corrected to match).
    await gotoApp('/deals/new');
    await page.getByRole('textbox', { name: 'Name', exact: true }).fill(`UAT PW Deal ${stamp}`);
    await page.getByRole('spinbutton', { name: 'Amount', exact: true }).fill('99000');
    await page.getByRole('button', { name: 'Create' }).click();
    await page.waitForURL(/\/deals(\/|$)/);
    await page.getByText(`UAT PW Deal ${stamp}`).first().waitFor();
    ok('deal create');

    // Deal detail: deal probability advisory is out of scope for Jev (ADR-015) — the panel must
    // stay visible and explicitly labelled 'disabled', never silently omitted.
    try {
      const panel = page.getByTestId('agent-panel');
      await panel.waitFor({ state: 'visible', timeout: 8000 });
      const text = (await panel.innerText()).replace(/\s+/g, ' ');
      if (!/out of scope for Jev advisory/i.test(text)) {
        throw new Error(`disabled-state copy not found in agent panel: "${text.slice(0, 160)}"`);
      }
      ok('deal detail agent panel disabled + labelled');
    } catch (e) {
      fail('deal detail agent panel disabled + labelled', String(e.message || e));
    }

    await searchList('/deals', '/api/v1/deals', 'Search opportunities...', `UAT PW Deal ${stamp}`);
    await expectDataTable(page, 'deals list shows app-data-table');

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

    // Quote DRAFT detail: additive. quote-service is frequently down during CRM-only UAT —
    // a non-2xx or empty result is a graceful SKIP, never a FAIL (per AGENTS.md: CPQ-down is
    // expected and proves nothing about the CRM feature under test).
    await checkQuoteDraftAgentPanel(page);
  } catch (e) {
    fail('fatal', String(e.stack || e));
  } finally {
    await browser.close();
  }

  const failed = results.filter((r) => r.status === 'FAIL');
  const skipped = results.filter((r) => r.status === 'SKIP');
  const passed = results.length - failed.length - skipped.length;
  console.log(`\n${passed}/${results.length} passed, ${skipped.length} skipped, ${failed.length} failed`);
  process.exit(failed.length ? 1 : 0);
}

/**
 * Best-effort: find an existing DRAFT quote via the API and confirm its detail page shows the
 * approval-tier advisory agent panel (only rendered while `canSubmit()`, i.e. DRAFT and not
 * ramp-pricing-blocked). Skips — does not fail — when quote-service is unreachable or there is
 * no DRAFT quote to look at; both are expected states for this stack (quote-service is routinely
 * down during CRM-focused UAT).
 */
async function checkQuoteDraftAgentPanel(page) {
  const NAME = 'quote DRAFT detail agent panel present';
  let response;
  try {
    response = await page.request.get(`${BASE}/api/v1/quotes?status=DRAFT&page=0&size=1`);
  } catch (e) {
    skip(NAME, `quote-service unreachable: ${String(e.message || e)}`);
    return;
  }
  if (!response.ok()) {
    skip(NAME, `quote-service returned HTTP ${response.status()} (expected when the service is down)`);
    return;
  }
  let body;
  try {
    body = await response.json();
  } catch (e) {
    skip(NAME, `could not parse quote list response: ${String(e.message || e)}`);
    return;
  }
  const draft = (body?.content ?? [])[0];
  if (!draft?.id) {
    skip(NAME, 'no DRAFT quote available in this environment');
    return;
  }
  try {
    await page.goto(`${BASE}/quotes/${draft.id}`, { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle').catch(() => {});
    const panel = page.getByTestId('agent-panel');
    await panel.waitFor({ state: 'visible', timeout: 8000 });
    const text = (await panel.innerText()).replace(/\s+/g, ' ');
    if (!/Approval tier advisory/i.test(text)) {
      throw new Error(`agent panel present but not the approval-tier advisory: "${text.slice(0, 160)}"`);
    }
    ok(NAME, `quote #${draft.id}`);
  } catch (e) {
    fail(NAME, String(e.message || e));
  }
}

main();
