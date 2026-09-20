/**
 * Quotes suite: list shell, create-form field contract, create through the UI (runCrud — there is
 * no edit *page*; the header is edited in a panel on the detail page), list behaviour, then the
 * detail-page workflows and the status lifecycle from workflows/quote-lifecycle.mjs.
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, defect, gap, waitUntil } from '../../lib/health.mjs';
import { controlLocator, fillField, readField, selectOptions, scopeLocator } from '../../lib/fields.mjs';
import { settleOverlays } from '../../lib/overlays.mjs';
import { ensure } from '../fixtures.mjs';
import { list, detail, filters, STATUS_LABELS } from '../specs/quotes.mjs';
import { runShell } from '../runners/shell.mjs';
import { runFieldContract } from '../runners/fieldContract.mjs';
import { runCrud } from '../runners/crud.mjs';
import {
  API, runDetailStructure, runHeaderEdit, runLineItems, runCalculate, runDocuments,
  runCloneCancelDelete, runSubmitOutage, runLifecycle,
} from '../workflows/quote-lifecycle.mjs';

const squash = (text) => (text ?? '').replace(/\s+/g, ' ').trim();
const main = (page) => scopeLocator(page, { kind: 'page' });

/** The real <input>; the host <app-search-input> carries the same placeholder attribute. */
const searchBox = (page) => page.getByRole('textbox', { name: list.searchPlaceholder, exact: true });

async function openList(page) {
  const listed = page.waitForResponse((r) => r.url().includes(`${API}?`) && r.ok(), { timeout: 15000 }).catch(() => null);
  await goto(page, list.path);
  await listed;
}

/** Narrow the (client-side) search to one run-unique name and return that row. */
async function findRow(page, name) {
  await openList(page);
  await searchBox(page).fill(name);
  const row = page.getByRole('row').filter({ hasText: name }).first();
  await row.waitFor({ state: 'visible' });
  return row;
}

const dataRowNames = async (page) =>
  (await page.locator('tr.table-row td:nth-child(2)').allInnerTexts()).map(squash);

export default {
  id: 'quotes',
  backend: 'quote',

  async run(ctx) {
    const { page, api } = ctx;
    const createForm = list.forms[0];

    await runShell(ctx, list);
    await runFieldContract(ctx, list, createForm);

    await runCheck(ctx, 'quotes.create.go-back', async () => {
      await goto(page, '/quotes/new');
      await page.getByRole('button', { name: 'Go back', exact: true }).click();
      await page.waitForURL((url) => url.pathname === list.path);
    });

    await runCheck(ctx, 'quotes.create.cancel', async () => {
      await goto(page, '/quotes/new');
      await page.getByRole('button', { name: 'Cancel', exact: true }).click();
      await page.waitForURL((url) => url.pathname === list.path);
    });

    // Create through the UI. No edit page and Delete lives in the More menu, so the runner only
    // creates + verifies; header edit and delete are covered by the workflows below.
    const created = await runCrud(ctx, list, createForm, {
      entity: 'quote',
      apiPath: API,
      nameField: 'Name',
      detailUrl: /\/quotes\/(\d+)$/,
      editPath: (id) => `/quotes/${id}`,
    });

    await runCheck(ctx, 'quotes.create.defaults-applied', async () => {
      const quote = await ensure(ctx, 'quote');
      const problems = [];
      if (quote.status !== 'DRAFT') problems.push(`status ${quote.status}`);
      if (quote.dealType !== 'NEW') problems.push(`dealType ${quote.dealType}`);
      if (!quote.quoteNumber) problems.push('no quoteNumber');
      if (problems.length) throw new Error(problems.join('; '));
      return `${quote.quoteNumber}: DRAFT / NEW / ${quote.currency} / ${quote.durationInMonths} months`;
    });

    // H-CPQ-Q-011: Currency is optional and the request omits it when blank; the backend defaults
    // dealType and duration but not currency, so the quote is stored with currency=null.
    await runCheck(ctx, 'quotes.create.currency-defaults-when-blank', async () => {
      await goto(page, '/quotes/new');
      const name = `UAT-${ctx.runId}-quote-nocurrency`;
      await fillField(page, main(page), createForm.fields[0], name);
      await fillField(page, main(page), createForm.fields[1], 'CUST-GOLD-001');
      await main(page).getByRole('button', { name: 'Create quote', exact: true }).click();
      await page.waitForURL(/\/quotes\/(\d+)$/, { timeout: 15000 });
      const id = page.url().match(/\/quotes\/(\d+)$/)[1];
      api.track('quote', id, `${API}/${id}`);
      const dto = await api.get(`${API}/${id}`);
      if (!dto.currency) throw defect(`quote ${id} created with a blank Currency is stored with currency=${JSON.stringify(dto.currency ?? null)} (dealType "${dto.dealType}" and duration ${dto.durationInMonths} were defaulted; the UI silently falls back to USD for display)`);
      return `currency defaulted to ${dto.currency}`;
    }, { hypothesis: 'H-CPQ-Q-011' });

    // H-CPQ-Q-001: dealType can be filtered on but never chosen.
    await runCheck(ctx, 'quotes.create.deal-type-field', async () => {
      await goto(page, '/quotes/new');
      await controlLocator(main(page), createForm.fields[0]).waitFor({ state: 'visible' });
      const offered = await page.getByRole('combobox', { name: /deal type/i }).count()
        + await page.getByRole('textbox', { name: /deal type/i }).count();
      if (!offered) throw gap('the create form has no Deal type control (every quote becomes NEW) although the list filters on AMENDMENT / RENEWAL / EXPANSION; the header panel cannot change it either');
      return 'a Deal type control is offered';
    }, { hypothesis: 'H-CPQ-Q-001' });

    /* ---------------------------------------------------------------- list behaviour */

    const first = await ensure(ctx, 'quote');
    const second = await ensure(ctx, 'quote', { customerExternalId: `CUST-${ctx.runId}` });

    await runCheck(ctx, 'quotes.list.filters.options', async () => {
      await openList(page);
      const seen = [];
      for (const filter of Object.values(filters)) {
        const actual = await selectOptions(page, main(page), filter);
        const expected = filter.options.map((o) => o.label);
        if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`${filter.ariaLabel}: options are [${actual.join(' | ')}]`);
        seen.push(`${filter.ariaLabel}: ${actual.length}`);
      }
      return seen.join(' · ');
    });

    await runCheck(ctx, 'quotes.list.search.by-name', async () => {
      const row = await findRow(page, first.name);
      // The search box debounces: wait for the filter to settle instead of reading rows once.
      await waitUntil(() => dataRowNames(page), (names) => names.length === 1 && names[0] === first.name, {
        describe: (names) => `search left ${names.length} rows: ${names.join(', ')}`,
      });
      return squash(await row.innerText());
    });

    await runCheck(ctx, 'quotes.list.search.by-number-and-customer', async () => {
      await openList(page);
      await searchBox(page).fill(first.quoteNumber);
      await page.getByRole('row').filter({ hasText: first.name }).first().waitFor({ state: 'visible' });
      await searchBox(page).fill(second.customerExternalId.toLowerCase());
      await page.getByRole('row').filter({ hasText: second.name }).first().waitFor({ state: 'visible' });
      await waitUntil(() => dataRowNames(page), (names) => names.length === 1, { describe: (n) => `customer search left rows: ${n.join(', ')}` });
    });

    await runCheck(ctx, 'quotes.list.search.no-match-empty-state', async () => {
      await openList(page);
      await searchBox(page).fill(`zz-no-such-quote-${ctx.runId}`);
      await page.getByText('No matching quotes', { exact: true }).waitFor({ state: 'visible' });
      await page.getByText('Try another search, status, or deal type filter.', { exact: true }).waitFor({ state: 'visible' });
      await page.getByRole('button', { name: 'Clear search', exact: true }).click();
      await page.getByRole('row').filter({ hasText: first.name }).first().waitFor({ state: 'visible' });
    });

    await runCheck(ctx, 'quotes.list.filter.status-is-server-side', async () => {
      await openList(page);
      const filtered = page.waitForResponse((r) => r.url().includes(`${API}?`) && r.url().includes('status=CANCELLED'), { timeout: 10000 })
        .catch(() => null);
      await fillField(page, main(page), filters.status, 'CANCELLED');
      const response = await filtered;
      if (!response) throw new Error('choosing a status did not request ?status=CANCELLED');
      await searchBox(page).fill(first.name); // a DRAFT quote must not survive a CANCELLED filter
      await page.getByText('No matching quotes', { exact: true }).waitFor({ state: 'visible' });
      await fillField(page, main(page), filters.status, 'All statuses');
      await page.getByRole('row').filter({ hasText: first.name }).first().waitFor({ state: 'visible' });
      return 'GET ?status=CANCELLED; DRAFT fixture hidden, back after "All statuses"';
    });

    await runCheck(ctx, 'quotes.list.filter.deal-type-is-server-side', async () => {
      await openList(page);
      const filtered = page.waitForResponse((r) => r.url().includes(`${API}?`) && r.url().includes('dealType=RENEWAL'), { timeout: 10000 })
        .catch(() => null);
      await fillField(page, main(page), filters.dealType, 'RENEWAL');
      if (!(await filtered)) throw new Error('choosing a deal type did not request ?dealType=RENEWAL');
      await searchBox(page).fill(first.name);
      await page.getByText('No matching quotes', { exact: true }).waitFor({ state: 'visible' });
      if ((await readField(main(page), filters.dealType)) !== 'RENEWAL') throw new Error('filter lost its selection');
    });

    await runCheck(ctx, 'quotes.list.row.click-opens-detail', async () => {
      const row = await findRow(page, first.name);
      await row.getByRole('cell').first().click();
      await page.waitForURL((url) => url.pathname === `/quotes/${first.id}`);
    });

    // H-CPQ-Q-002: one status, several spellings.
    await runCheck(ctx, 'quotes.list.status-vocabulary', async () => {
      const row = await findRow(page, first.name);
      const cell = squash(await row.getByRole('cell').nth(3).innerText());
      const option = (await selectOptions(page, main(page), filters.status))[1];
      await goto(page, `/quotes/${first.id}`);
      const badge = squash(await scopeLocator(page, { kind: 'card', title: 'Quote details' }).locator('app-status-badge').innerText());
      const spellings = new Set([cell, option, badge]);
      if (spellings.size > 1) {
        throw defect(`DRAFT is spelled three ways: list filter option "${option}", list Status cell "${cell}", detail badge "${badge}" (expected the friendly "${STATUS_LABELS.DRAFT}" everywhere)`);
      }
      return `consistently "${badge}"`;
    }, { hypothesis: 'H-CPQ-Q-002' });

    // H-CPQ-Q-003: the headers take the click and show an arrow, but nothing is wired to it.
    await runCheck(ctx, 'quotes.list.sort-headers', async () => {
      await openList(page);
      await searchBox(page).fill(`UAT-${ctx.runId}-`);
      const prefix = `UAT-${ctx.runId}-`;
      await waitUntil(() => dataRowNames(page), (names) => names.length >= 2 && names.every((n) => n.startsWith(prefix)), {
        describe: (n) => `need >= 2 rows of this run only, saw: ${n.join(', ')}`,
      });
      const before = await dataRowNames(page);
      const sortRequests = [];
      const onRequest = (request) => {
        if (request.url().includes(`${API}?`)) sortRequests.push(new URL(request.url()).search);
      };
      page.on('request', onRequest);
      const header = page.getByRole('columnheader', { name: 'Name', exact: true });
      const orders = [];
      const arrows = [];
      try {
        for (let click = 0; click < 2; click++) {
          await header.click();
          await page.waitForTimeout(700);
          arrows.push(await header.getAttribute('aria-sort'));
          orders.push(await dataRowNames(page));
        }
      } finally {
        page.off('request', onRequest);
      }
      const reordered = orders.some((o) => JSON.stringify(o) !== JSON.stringify(before));
      if (!reordered && !sortRequests.length) {
        throw defect(`clicking "Name" twice set aria-sort to ${arrows.join(' then ')} but sent no request and left ${before.length} rows in the same order`);
      }
      return reordered ? `rows reordered (aria-sort ${arrows.join(', ')})` : `requests: ${sortRequests.join(' ')}`;
    }, { hypothesis: 'H-CPQ-Q-003' });

    // H-CPQ-Q-007: WebMCP tools are registered through an experimental Angular API; whether the
    // browser exposes navigator.modelContext decides if they are reachable at all. INFO only.
    if (!ctx.only || ctx.only.some((prefix) => 'quotes.list.webmcp'.startsWith(prefix))) {
      await openList(page);
      const kind = await page.evaluate(() => typeof navigator.modelContext).catch((error) => `evaluate failed: ${error.message}`);
      ctx.report.info('quotes.list.webmcp', kind === 'undefined'
        ? 'typeof navigator.modelContext === "undefined" in this Chromium — listQuotes/filterQuotes/pageQuotes cannot be observed from the browser; the page loads without errors regardless'
        : `typeof navigator.modelContext === "${kind}" — WebMCP host API is present`);
      await settleOverlays(page);
    }

    /* ---------------------------------------------------------------- detail page */

    const subject = await ensure(ctx, 'quote', { region: 'NA', description: 'Detail fixture' });
    await runShell(ctx, { ...detail, id: 'quotes.detail', tables: undefined }, { path: `/quotes/${subject.id}`, heading: subject.name });
    await runDetailStructure(ctx, subject);
    await runHeaderEdit(ctx, subject);
    await runLineItems(ctx, subject);
    await runCalculate(ctx);
    await runDocuments(ctx, subject);
    await runCloneCancelDelete(ctx);

    /* ---------------------------------------------------------------- lifecycle */

    await runSubmitOutage(ctx);
    await runLifecycle(ctx);

    return created;
  },
};
