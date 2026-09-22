/**
 * Catalog suite: list shell + filters, the New Product signal-form dialog, product CRUD through
 * the dialog and the in-page edit mode, list behaviour (search / sort / filter hypotheses), and
 * the three detail-page panels (delegated to workflows/catalog-detail-panels.mjs).
 *
 * `runCrud` does not fit here: the create form is a dialog with TWO unique fields (SKU + Name),
 * and "edit" is an in-page mode with a different field set whose Save/Cancel live in the page
 * header — so the CRUD is hand-written with `runSteps`, driven by the same spec data.
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, runSteps, defect, gap, waitUntil } from '../../lib/health.mjs';
import { confirmMatDialog } from '../../lib/dialogs.mjs';
import { expectNoSnackbar, dismissSnackbars } from '../../lib/overlays.mjs';
import { scopeLocator, controlLocator, fillField, readField, fieldError, selectOptions, normalizeValue, readPath } from '../../lib/fields.mjs';
import { ApiCallError } from '../../lib/api.mjs';
import { ensure, uniqueName, runPrefix } from '../fixtures.mjs';
import { list, detail, CATEGORIES, STATUSES } from '../specs/catalog.mjs';
import { runShell } from '../runners/shell.mjs';
import { runFieldContract, openForm } from '../runners/fieldContract.mjs';
import { runDetailPanels } from '../workflows/catalog-detail-panels.mjs';

const API = '/api/v1/products';
const DETAIL_URL = /\/catalog\/(\d+)$/;

const createForm = list.forms[0];
const editForm = detail.forms[0];
const field = (form, label) => form.fields.find((f) => f.label === label);

const CATEGORY_FILTER = { label: 'Category', ariaLabel: 'Filter by category', kind: 'select' };
const STATUS_FILTER = { label: 'Status', ariaLabel: 'Filter by status', kind: 'select' };

/** The real <input>; the <app-search-input> host repeats the placeholder attribute. */
const searchBox = (page) => page.getByRole('textbox', { name: list.searchPlaceholder, exact: true });
const dataRows = (page) => page.locator('tr.table-row');
const main = (page) => scopeLocator(page, { kind: 'page' });

/** Load the list and search; resolves once the matching search response has arrived. */
async function searchFor(page, term) {
  await goto(page, list.path);
  const responded = page
    .waitForResponse((r) => r.url().includes(`${API}/search`) && r.url().includes(encodeURIComponent(term)) && r.ok(), { timeout: 10000 })
    .catch(() => null);
  await searchBox(page).fill(term);
  const response = await responded;
  if (!response) throw new Error(`no ${API}/search response for "${term}"`);
  return new URL(response.url());
}

async function findRow(page, term) {
  await searchFor(page, term);
  const row = dataRows(page).filter({ hasText: term }).first();
  await row.waitFor({ state: 'visible' });
  return row;
}

const cellTexts = async (page, column) =>
  (await dataRows(page).locator(`td:nth-child(${column + 1})`).allInnerTexts()).map((t) => t.trim());

async function expectGone(api, path) {
  try {
    await api.get(path);
  } catch (error) {
    if (error instanceof ApiCallError && error.status === 404) return `GET ${path} -> 404`;
    throw error;
  }
  throw new Error(`${path} is still readable through the API after delete`);
}

/** Compare what was typed against the API for every field the spec says persists in `mode`. */
function persistProblems(form, typed, dto, mode) {
  const problems = [];
  for (const f of form.fields) {
    if (!f.persist?.[mode] || !(f.label in typed)) continue;
    const want = f.kind === 'number' ? Number(typed[f.label]) : normalizeValue(typed[f.label], f.normalize);
    const got = readPath(dto, f.persist.apiPath);
    if (String(got ?? '') !== String(want ?? '')) problems.push(`${f.label}: typed "${want}", API holds "${got}"`);
  }
  return problems;
}

export default {
  id: 'catalog',
  backend: 'product',

  async run(ctx) {
    const { page, api, runId } = ctx;
    const prefix = runPrefix(runId);

    // A LICENSE fixture guarantees the table (not the empty state) renders and gives the filter
    // checks a row to find.
    const fixture = await ensure(ctx, 'product', { category: 'LICENSE', description: 'Detail fixture' });

    // ── list shell ────────────────────────────────────────────────────────────────────────────
    await runShell(ctx, list);

    for (const [name, filter, values, all] of [
      ['category', CATEGORY_FILTER, CATEGORIES, 'All categories'],
      ['status', STATUS_FILTER, STATUSES, 'All statuses'],
    ]) {
      await runCheck(ctx, `catalog.filter.${name}.options`, async () => {
        await goto(page, list.path);
        const actual = await selectOptions(page, main(page), filter);
        const expected = [all, ...values];
        if (JSON.stringify(actual) !== JSON.stringify(expected)) {
          throw new Error(`options differ — expected [${expected.join(' | ')}], saw [${actual.join(' | ')}]`);
        }
        return actual.join(' | ');
      });
    }

    // ── New Product dialog ────────────────────────────────────────────────────────────────────
    await runFieldContract(ctx, list, createForm);

    await runCheck(ctx, 'catalog.dialog.escape-does-not-close', async () => {
      const dialog = await openForm(ctx, list, createForm);
      await page.keyboard.press('Escape');
      await page.waitForTimeout(500);
      if (!(await dialog.isVisible())) throw new Error('Escape closed the dialog although it is opened with disableClose');
      await dialog.getByRole('button', { name: 'Cancel', exact: true }).click();
      await dialog.waitFor({ state: 'hidden' });
      if (new URL(page.url()).pathname !== list.path) throw new Error(`Cancel navigated to ${page.url()}`);
      return 'Escape ignored; Cancel closes and stays on /catalog';
    });

    // H-CPQ-P-005: settle what maxLength(3) does for a *typing* user. fill() would bypass a
    // maxlength attribute, so this check uses real keystrokes.
    await runCheck(ctx, 'catalog.field.Currency.maxlength-typing', async () => {
      const dialog = await openForm(ctx, list, createForm);
      for (const f of createForm.fields.filter((x) => x.required)) await fillField(page, dialog, f, f.sample);
      const currency = field(createForm, 'Currency');
      const input = controlLocator(dialog, currency);
      const maxAttr = await input.getAttribute('maxlength');
      await input.click();
      await input.pressSequentially('USDD');
      await page.keyboard.press('Tab');
      const error = await fieldError(dialog, currency);
      const value = await input.inputValue();
      const createEnabled = await dialog.getByRole('button', { name: 'Create', exact: true }).isEnabled();
      if (value.length <= 3) {
        return `maxlength="${maxAttr}" truncates typing to "${value}" — the maxLength(3) message is unreachable from the keyboard (dead copy, not a defect)`;
      }
      if (error === 'Currency must be 3 characters' && !createEnabled) {
        return `no maxlength attribute (maxlength=${maxAttr}); "${value}" is kept, mat-error "${error}" shown and Create disabled`;
      }
      throw defect(`"${value}" is accepted: mat-error=${JSON.stringify(error)}, Create ${createEnabled ? 'enabled' : 'disabled'}`);
    }, { hypothesis: 'H-CPQ-P-005' });

    // H-CPQ-P-014 (found while reading the schema): signal-forms `required` accepts a
    // whitespace-only string, the component then trims it to '' and the server rejects it.
    await runCheck(ctx, 'catalog.dialog.whitespace-sku-rejected-client-side', async () => {
      const dialog = await openForm(ctx, list, createForm);
      for (const f of createForm.fields.filter((x) => x.required)) await fillField(page, dialog, f, f.sample);
      await fillField(page, dialog, field(createForm, 'SKU'), '   ');
      await page.keyboard.press('Tab');
      const create = dialog.getByRole('button', { name: 'Create', exact: true });
      const enabled = await waitUntil(() => create.isEnabled(), (v) => v === false, { timeoutMs: 1500 }).then(() => false).catch(() => true);
      if (enabled) throw defect('Create is enabled for a whitespace-only SKU (required() does not trim; the server is left to reject it)');
      return 'Create disabled for whitespace-only SKU';
    }, { hypothesis: 'H-CPQ-P-014' });

    await runCheck(ctx, 'catalog.dialog.server-error-banner', async () => {
      const dialog = await openForm(ctx, list, createForm);
      for (const f of createForm.fields.filter((x) => x.required)) await fillField(page, dialog, f, f.sample);
      await fillField(page, dialog, field(createForm, 'SKU'), fixture.sku); // duplicate
      await dialog.getByRole('button', { name: 'Create', exact: true }).click();
      const banner = dialog.getByRole('alert');
      await banner.waitFor({ state: 'visible' });
      const text = (await banner.innerText()).replace(/\s+/g, ' ').trim();
      if (!/already exists/i.test(text)) throw new Error(`banner says "${text}", expected the duplicate-SKU message`);
      if (!(await dialog.isVisible())) throw new Error('dialog closed although the create failed');
      if (new URL(page.url()).pathname !== list.path) throw new Error('navigated away although the create failed');
      await dismissSnackbars(page); // errorInterceptor toasts the same message globally
      return text;
    });

    // ── CRUD through the UI ───────────────────────────────────────────────────────────────────
    const state = { id: null, created: {}, edited: {} };
    const sku = uniqueName(runId, 'sku');
    const name = uniqueName(runId, 'catalog');
    const newName = `${name}-edited`;
    const detailCard = () => scopeLocator(page, { kind: 'card', title: 'Product details' });
    const editCard = () => scopeLocator(page, { kind: 'card', title: 'Edit product' });
    const headerButton = (label) => page.getByRole('button', { name: label, exact: true }).first();

    await runSteps(ctx, [
      {
        id: 'catalog.crud.create',
        run: async () => {
          const dialog = await openForm(ctx, list, createForm);
          for (const f of createForm.fields) {
            const value = f.label === 'SKU' ? sku : f.label === 'Name' ? name : f.sample;
            await fillField(page, dialog, f, value);
            state.created[f.label] = value;
          }
          await dialog.getByRole('button', { name: 'Create', exact: true }).click();
          await page.waitForURL(DETAIL_URL, { timeout: 15000 });
          state.id = page.url().match(DETAIL_URL)[1];
          api.track('product', state.id, `${API}/${state.id}`);
          return `${name} -> /catalog/${state.id}`;
        },
      },
      {
        // H-CPQ-P-013 (gap): success is silent by design — assert the documented behaviour.
        id: 'catalog.crud.create.no-success-snackbar',
        run: async () => { await expectNoSnackbar(page); return 'no snackbar after create'; },
      },
      {
        id: 'catalog.crud.persist.create',
        run: async () => {
          const dto = await api.get(`${API}/${state.id}`);
          const problems = persistProblems(createForm, state.created, dto, 'create');
          if (dto.status !== 'DRAFT') problems.push(`status is "${dto.status}", expected DRAFT for a new product`);
          if (problems.length) throw new Error(problems.join('; '));
          return `${Object.keys(state.created).length} fields verified through the API; status DRAFT`;
        },
      },
      {
        id: 'catalog.crud.detail',
        run: async () => {
          await goto(page, `/catalog/${state.id}`);
          await page.getByRole('heading', { level: 1 }).filter({ hasText: name }).waitFor({ state: 'visible' });
          await page.getByText(sku, { exact: true }).first().waitFor({ state: 'visible' });
          const text = (await detailCard().innerText()).replace(/\s+/g, ' ');
          const missing = ['Draft', 'SUBSCRIPTION', 'SAAS_FLAT_RATE', '49.50', '—'].filter((t) => !text.includes(t));
          if (missing.length) throw new Error(`details card lacks ${missing.join(', ')} — shows "${text}"`);
          return text.slice(0, 140);
        },
      },
      {
        id: 'catalog.crud.edit.cancel-discards',
        run: async () => {
          await goto(page, `/catalog/${state.id}`);
          await headerButton('Edit').click();
          await fillField(page, editCard(), field(editForm, 'Name'), 'should not be saved');
          await headerButton('Cancel').click();
          await detailCard().waitFor({ state: 'visible' });
          const dto = await api.get(`${API}/${state.id}`);
          if (dto.name !== name) throw new Error(`name became "${dto.name}" although the edit was cancelled`);
        },
      },
      {
        id: 'catalog.crud.edit.prefilled',
        run: async () => {
          await goto(page, `/catalog/${state.id}`);
          await headerButton('Edit').click();
          const card = editCard();
          await card.waitFor({ state: 'visible' });
          const expected = { Name: name, 'Product type': 'SAAS_FLAT_RATE', Category: 'SUBSCRIPTION', Status: 'DRAFT', 'List price': '49.5', Currency: 'USD' };
          const wrong = [];
          for (const [label, want] of Object.entries(expected)) {
            const got = await waitUntil(() => readField(card, field(editForm, label)), (v) => String(v) === want, { timeoutMs: 2000 }).catch(() => null);
            if (got === null) wrong.push(`${label} is "${await readField(card, field(editForm, label))}", expected "${want}"`);
          }
          if (wrong.length) throw new Error(wrong.join('; '));
          const statusOptions = await selectOptions(page, card, field(editForm, 'Status'));
          if (JSON.stringify(statusOptions) !== JSON.stringify(STATUSES)) throw new Error(`Status options are [${statusOptions.join(' | ')}]`);
          return `prefilled; Status options ${statusOptions.join(' | ')}`;
        },
      },
      {
        id: 'catalog.crud.edit.name-required',
        run: async () => {
          await goto(page, `/catalog/${state.id}`);
          await headerButton('Edit').click();
          const card = editCard();
          const nameField = field(editForm, 'Name');
          await fillField(page, card, nameField, '');
          await page.keyboard.press('Tab');
          const error = await fieldError(card, nameField);
          if (error !== 'Name is required') throw new Error(`mat-error is ${JSON.stringify(error)}, expected "Name is required"`);
          if (await headerButton('Save').isEnabled()) throw new Error('Save stays enabled with an empty Name');
          return `"${error}", Save disabled`;
        },
      },
      {
        id: 'catalog.crud.edit.save',
        run: async () => {
          await goto(page, `/catalog/${state.id}`);
          await headerButton('Edit').click();
          const card = editCard();
          await card.waitFor({ state: 'visible' });
          await fillField(page, card, field(editForm, 'Name'), newName);
          state.edited.Name = newName;
          for (const f of editForm.fields.filter((x) => x.editSample !== undefined)) {
            await fillField(page, card, f, f.editSample);
            state.edited[f.label] = f.editSample;
          }
          await headerButton('Save').click();
          await detailCard().waitFor({ state: 'visible' });
          await page.getByRole('heading', { level: 1 }).filter({ hasText: newName }).waitFor({ state: 'visible' });
          await expectNoSnackbar(page); // H-CPQ-P-013: silent success
          const text = (await detailCard().innerText()).replace(/\s+/g, ' ');
          const missing = ['Active', 'SERVICES', 'TRAINING', '99.25', 'Edited by the UAT.'].filter((t) => !text.includes(t));
          if (missing.length) throw new Error(`details card lacks ${missing.join(', ')} after save — shows "${text}"`);
          return `changed ${Object.keys(state.edited).join(', ')}`;
        },
      },
      {
        id: 'catalog.crud.persist.update',
        run: async () => {
          const dto = await api.get(`${API}/${state.id}`);
          const problems = persistProblems(editForm, state.edited, dto, 'update');
          if (dto.sku !== sku) problems.push(`SKU changed to "${dto.sku}"`);
          if (problems.length) throw new Error(problems.join('; '));
          return `${Object.keys(state.edited).length} fields verified through the API`;
        },
      },
      {
        id: 'catalog.crud.delete.cancel-keeps-record',
        run: async () => {
          await goto(page, `/catalog/${state.id}`);
          await headerButton('Delete').click();
          const confirm = detail.actions.find((a) => a.name === 'Delete').confirm;
          await confirmMatDialog(page, { title: confirm.title, message: confirm.message, click: confirm.cancelText });
          const still = await api.get(`${API}/${state.id}`);
          if (String(still?.id) !== String(state.id)) throw new Error('record disappeared although the confirm was cancelled');
          if (!DETAIL_URL.test(page.url())) throw new Error(`cancel navigated to ${page.url()}`);
        },
      },
      {
        id: 'catalog.crud.delete',
        run: async () => {
          await goto(page, `/catalog/${state.id}`);
          await headerButton('Delete').click();
          const confirm = detail.actions.find((a) => a.name === 'Delete').confirm;
          await confirmMatDialog(page, { title: confirm.title, message: confirm.message, click: confirm.confirmText });
          await page.waitForURL((url) => url.pathname === list.path, { timeout: 15000 });
          await expectNoSnackbar(page); // H-CPQ-P-013: silent success
          const proof = await expectGone(api, `${API}/${state.id}`);
          api.untrack('product', state.id);
          return proof;
        },
      },
    ]);

    // ── detail page structure ─────────────────────────────────────────────────────────────────
    await runShell(ctx, detail, { path: `/catalog/${fixture.id}`, heading: fixture.name });

    await runCheck(ctx, 'catalog.detail.not-found', async () => {
      await goto(page, '/catalog/999999999');
      await page.getByRole('heading', { name: 'Product not found', exact: true }).waitFor({ state: 'visible' });
      await dismissSnackbars(page); // the 404 is also toasted by errorInterceptor
      await page.getByRole('button', { name: 'Back to catalog', exact: true }).click();
      await page.waitForURL((url) => url.pathname === list.path);
    });

    // ── list behaviour ────────────────────────────────────────────────────────────────────────
    await runCheck(ctx, 'catalog.search.finds-by-name-and-sku', async () => {
      const row = await findRow(page, fixture.name);
      const bySku = await findRow(page, fixture.sku);
      await bySku.waitFor({ state: 'visible' });
      return (await row.innerText().catch(() => fixture.name)).replace(/\s+/g, ' ').slice(0, 100);
    });

    await runCheck(ctx, 'catalog.search.clear-restores-list', async () => {
      await findRow(page, fixture.name);
      await page.getByRole('button', { name: 'Clear search', exact: true }).click();
      await waitUntil(() => searchBox(page).inputValue(), (v) => v === '', {
        describe: (v) => `search box still holds "${v}" after Clear`,
      });
    });

    await runCheck(ctx, 'catalog.search.no-match-empty-state', async () => {
      await searchFor(page, `zz-${runId}-nomatch`);
      await page.getByRole('heading', { name: 'No matching products', exact: true }).waitFor({ state: 'visible' });
      await page.getByText('Try another search, category, or status filter.', { exact: true }).waitFor({ state: 'visible' });
      const action = page.locator('app-empty-state').getByRole('button');
      if (await action.count()) throw new Error('the filtered empty state offers an action button; the spec says it must not');
    });

    await runCheck(ctx, 'catalog.row.click-opens-detail', async () => {
      const row = await findRow(page, fixture.name);
      await row.locator('td').first().click();
      await page.waitForURL((url) => url.pathname === `/catalog/${fixture.id}`);
    });

    await runCheck(ctx, 'catalog.filter.category.server-side', async () => {
      await goto(page, list.path);
      const requested = page.waitForResponse((r) => r.url().includes(`${API}?`) && r.url().includes('category=LICENSE'), { timeout: 10000 }).catch(() => null);
      await fillField(page, main(page), CATEGORY_FILTER, 'LICENSE');
      if (!(await requested)) throw new Error('selecting Category=LICENSE did not request /products?category=LICENSE');
      await dataRows(page).first().waitFor({ state: 'visible' });
      const categories = await waitUntil(() => cellTexts(page, 2), (v) => v.length > 0 && v.every((c) => c === 'LICENSE'), {
        describe: (v) => `rows show categories [${[...new Set(v)].join(', ')}] under Category=LICENSE`,
      });
      return `${categories.length} rows, all LICENSE`;
    });

    await runCheck(ctx, 'catalog.filter.status.server-side', async () => {
      await goto(page, list.path);
      const requested = page.waitForResponse((r) => r.url().includes(`${API}?`) && r.url().includes('status=DRAFT'), { timeout: 10000 }).catch(() => null);
      await fillField(page, main(page), STATUS_FILTER, 'DRAFT');
      if (!(await requested)) throw new Error('selecting Status=DRAFT did not request /products?status=DRAFT');
      const statuses = await waitUntil(() => cellTexts(page, 3), (v) => v.length > 0 && v.every((c) => c === 'DRAFT'), {
        describe: (v) => `rows show statuses [${[...new Set(v)].join(', ')}] under Status=DRAFT`,
      });
      return `${statuses.length} rows, all DRAFT`;
    });

    // H-CPQ-P-002: mat-sort-header is active on every column but (sortChange) is not bound, so the
    // header announces a sort the rows never follow. Two rows whose SKU order is the reverse of
    // their name order make the no-op observable in one of the two directions.
    await runCheck(ctx, 'catalog.table.sort-header-sorts-rows', async () => {
      const token = `${prefix}SORT`;
      await ensure(ctx, 'product', { name: `${token}-A`, sku: `${token}-SKU-Z` });
      await ensure(ctx, 'product', { name: `${token}-B`, sku: `${token}-SKU-Y` });
      await searchFor(page, token);
      await waitUntil(() => dataRows(page).count(), (n) => n === 2, { describe: (n) => `expected 2 rows for "${token}", saw ${n}` });
      const header = page.getByRole('columnheader', { name: 'SKU', exact: true });
      const observed = [];
      for (const direction of ['ascending', 'descending']) {
        await header.click();
        const announced = await waitUntil(() => header.getAttribute('aria-sort'), (v) => v === direction, { timeoutMs: 2000 }).catch(() => null);
        if (!announced) return `header never announced aria-sort=${direction} — columns are not presented as sortable`;
        await page.waitForTimeout(600); // room for a (hypothetical) re-query to land
        const skus = await cellTexts(page, 0);
        const sorted = [...skus].sort();
        if (direction === 'descending') sorted.reverse();
        observed.push({ direction, skus, ok: JSON.stringify(skus) === JSON.stringify(sorted) });
      }
      const wrong = observed.filter((o) => !o.ok);
      if (wrong.length) {
        throw defect(wrong.map((o) => `SKU header announces aria-sort=${o.direction} but rows are [${o.skus.join(', ')}]`).join('; '));
      }
      return 'rows follow the announced sort in both directions';
    }, { hypothesis: 'H-CPQ-P-002' });

    // H-CPQ-P-003: TableColumn.type 'badge' has no @case in DataTableComponent.
    await runCheck(ctx, 'catalog.table.status-renders-as-badge', async () => {
      const row = await findRow(page, fixture.name);
      const cell = row.locator('td').nth(3);
      const shape = await cell.evaluate((el) => ({ text: el.textContent.trim(), children: el.children.length, html: el.innerHTML.trim().slice(0, 80) }));
      if (shape.children === 0) throw defect(`Status cell is the bare text "${shape.text}" — no chip/badge element (detail page uses <app-status-badge>)`);
      return shape.html;
    }, { hypothesis: 'H-CPQ-P-003' });

    // H-CPQ-P-006: list + filters print raw enum constants; the detail badge prints a label.
    await runCheck(ctx, 'catalog.consistency.status-wording', async () => {
      const row = await findRow(page, fixture.name);
      const listed = (await row.locator('td').nth(3).innerText()).trim();
      await goto(page, `/catalog/${fixture.id}`);
      const badge = (await page.locator('app-status-badge').first().innerText()).trim();
      if (listed !== badge) throw defect(`same product, same status: list/filter say "${listed}", detail badge says "${badge}"`);
      return `"${listed}" everywhere`;
    }, { hypothesis: 'H-CPQ-P-006' });

    // H-CPQ-P-004: with a search term active the Category/Status filters only refine the rows of
    // the *current search page*. A match that sits on search page 2 must therefore vanish.
    await runCheck(ctx, 'catalog.filter.category-with-search-finds-all-matches', async () => {
      const token = `${prefix}H4`;
      const probe = await searchFor(page, token);
      const size = Number(probe.searchParams.get('size')) || 20;
      for (let i = 0; i <= size; i++) {
        await ensure(ctx, 'product', { name: `${token}-${String(i).padStart(2, '0')}`, sku: `${token}-SKU-${String(i).padStart(2, '0')}` });
      }
      const pageTwo = await api.get(`${API}/search?q=${encodeURIComponent(token)}&page=1&size=${size}`);
      const target = pageTwo?.content?.[0];
      if (!target) throw new Error(`fixture setup: search page 2 is empty (totalElements ${pageTwo?.totalElements})`);
      await api.put(`${API}/${target.id}`, { category: 'SUPPORT' });
      const recheck = await api.get(`${API}/search?q=${encodeURIComponent(token)}&page=1&size=${size}`);
      if (!recheck.content.some((p) => p.id === target.id && p.category === 'SUPPORT')) {
        throw new Error('fixture setup: the SUPPORT product did not stay on search page 2');
      }

      await searchFor(page, token);
      await waitUntil(() => dataRows(page).count(), (n) => n === size, { describe: (n) => `expected ${size} rows on search page 1, saw ${n}` });
      await fillField(page, main(page), CATEGORY_FILTER, 'SUPPORT');
      const found = await dataRows(page).filter({ hasText: target.name }).first()
        .waitFor({ state: 'visible', timeout: 4000 }).then(() => true).catch(() => false);
      if (found) return `${target.name} (SUPPORT, search page 2) is listed`;
      const rows = await dataRows(page).count();
      const empty = await page.getByRole('heading', { name: 'No matching products', exact: true }).isVisible().catch(() => false);
      throw defect(`search "${token}" + Category=SUPPORT shows ${rows} rows${empty ? ' and "No matching products"' : ''}, but ${target.name} is SUPPORT and matches — the filter only refines search page 1 of ${pageTwo.totalElements} results`);
    }, { hypothesis: 'H-CPQ-P-004' });

    // H-CPQ-P-009 (gap): ProductService has activate/deprecate/retire; no UI calls them.
    await runCheck(ctx, 'catalog.gap.lifecycle-actions', async () => {
      await goto(page, `/catalog/${fixture.id}`);
      await page.getByRole('button', { name: 'Edit', exact: true }).waitFor({ state: 'visible' });
      const found = [];
      for (const label of ['Activate', 'Deprecate', 'Retire']) {
        if (await page.getByRole('button', { name: label }).count()) found.push(label);
      }
      if (found.length) return `lifecycle actions now exist: ${found.join(', ')}`;
      throw gap('no Activate / Deprecate / Retire action on the product detail page; status can only be changed through the Edit form select');
    }, { hypothesis: 'H-CPQ-P-009' });

    // ── detail panels ─────────────────────────────────────────────────────────────────────────
    await runDetailPanels(ctx);
  },
};
