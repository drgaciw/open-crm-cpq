/**
 * Price books suite: shell + field contracts from the spec, then hand-written CRUD (the forms are
 * dialogs that navigate to /price-books/:id, which the page-form CRUD runner cannot drive), the
 * entries workflow and the immutability workflow.
 *
 * Every price book needs its own region (backend: DUPLICATE_REGION_PRICE_BOOK), so fixtures always
 * override the fixture default 'NA'. "Default price book" is never ticked: a default book with no
 * effective date becomes the fallback book for every pricing call on the shared stack.
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, runSteps, defect, waitUntil } from '../../lib/health.mjs';
import { scopeLocator, fillField, readField, fieldError, blurField } from '../../lib/fields.mjs';
import { confirmMatDialog } from '../../lib/dialogs.mjs';
import { expectSnackbar, dismissSnackbars } from '../../lib/overlays.mjs';
import { ApiCallError } from '../../lib/api.mjs';
import { ensure, uniqueName } from '../fixtures.mjs';
import { list, editForm, detailFor, DATE_FIELDS, ENTRY_COLUMNS } from '../specs/price-books.mjs';
import { runShell } from '../runners/shell.mjs';
import { runFieldContract, openForm } from '../runners/fieldContract.mjs';
import {
  API, ENTRIES_5XX, tolerantCtx, uniqueRegion, openDetail, detailValue, runPriceBookImmutability,
} from '../workflows/price-book-immutability.mjs';

const squash = (text) => (text ?? '').replace(/\s+/g, ' ').trim();
const field = (form, label) => form.fields.find((f) => f.label === label);
const banner = (scope) => scope.locator('.error-banner[role="alert"]');
const regionBox = (page) => page.getByRole('textbox', { name: 'Filter by region', exact: true });

/** Apply the region filter and wait for the filtered request to answer. */
async function filterByRegion(page, region, { viaEnter = false } = {}) {
  await goto(page, list.path);
  const responded = page
    .waitForResponse((r) => r.url().includes(`${API}?`) && r.url().includes(`region=${encodeURIComponent(region)}`), { timeout: 10000 })
    .catch(() => null);
  await regionBox(page).fill(region);
  if (viaEnter) await regionBox(page).press('Enter');
  else await page.getByRole('button', { name: 'Apply region filter', exact: true }).click();
  if (!(await responded)) throw new Error(`no list request carrying region=${region} was sent`);
}

async function expectGone(api, path) {
  try {
    await api.get(path);
  } catch (error) {
    if (error instanceof ApiCallError && error.status === 404) return `GET ${path} -> 404`;
    throw error;
  }
  throw new Error(`${path} is still readable after delete`);
}

export default {
  id: 'price-books',
  backend: 'pricebook',

  async run(ctx) {
    const { page, api, runId } = ctx;
    const createForm = list.forms[0];
    const tolerate = { expectedErrors: [ENTRIES_5XX] };

    // Two API fixtures: the list needs rows before its table renders, and sorting needs two.
    const bookA = await ensure(ctx, 'priceBook', { name: uniqueName(runId, 'AAA-book'), region: uniqueRegion(ctx) });
    const bookZ = await ensure(ctx, 'priceBook', { name: uniqueName(runId, 'ZZZ-book'), region: uniqueRegion(ctx), currency: 'EUR' });

    await runShell(ctx, list);
    await runFieldContract(ctx, list, createForm);

    await runCheck(ctx, 'price-books.field.dates.present', async () => {
      const scope = await openForm(ctx, list, createForm);
      for (const label of DATE_FIELDS) {
        const input = scope.getByLabel(label, { exact: true });
        await input.waitFor({ state: 'visible' });
        const type = await input.getAttribute('type');
        if (type !== 'date') throw new Error(`"${label}" is type="${type}", expected a native date input`);
        if ((await input.inputValue()) !== '') throw new Error(`"${label}" is prefilled`);
      }
      return DATE_FIELDS.join(', ');
    });

    // The inputs carry maxlength attributes equal to the validators, so the maxLength mat-errors
    // ("Name must be at most 255 characters", "Currency must be 3 characters") can never render.
    await runCheck(ctx, 'price-books.field.maxlength-caps-input', async () => {
      const scope = await openForm(ctx, list, createForm);
      await fillField(page, scope, field(createForm, 'Name'), 'N'.repeat(300));
      await fillField(page, scope, field(createForm, 'Currency'), 'USDX');
      await blurField(page, scope, field(createForm, 'Currency'));
      const nameLength = (await readField(scope, field(createForm, 'Name'))).length;
      const currency = await readField(scope, field(createForm, 'Currency'));
      if (nameLength > 255) throw new Error(`Name accepted ${nameLength} characters`);
      if (currency.length > 3) throw new Error(`Currency accepted "${currency}"`);
      return `Name capped at ${nameLength}, Currency at "${currency}" — the maxLength mat-errors are unreachable`;
    });

    // H-CPQ-PR-011: the message says "must be 3 characters" but only maxLength(3) is enforced.
    await runCheck(ctx, 'price-books.field.Currency.short-code-rejected', async () => {
      const scope = await openForm(ctx, list, createForm);
      await fillField(page, scope, field(createForm, 'Name'), 'UAT currency probe');
      await fillField(page, scope, field(createForm, 'Currency'), 'US');
      await blurField(page, scope, field(createForm, 'Currency'));
      const shown = await fieldError(scope, field(createForm, 'Currency'));
      const enabled = await scope.getByRole('button', { name: 'Create', exact: true }).isEnabled();
      if (!shown && enabled) throw defect('currency "US" (2 letters) raises no mat-error and Create stays enabled');
      return `mat-error "${shown}"`;
    }, { hypothesis: 'H-CPQ-PR-011' });

    await runCheck(ctx, 'price-books.create.escape-does-not-close', async () => {
      const scope = await openForm(ctx, list, createForm);
      await page.keyboard.press('Escape');
      await page.waitForTimeout(400);
      if (!(await scope.isVisible())) throw new Error('Escape closed a disableClose dialog');
    });

    await runCheck(ctx, 'price-books.create.cancel-creates-nothing', async () => {
      const scope = await openForm(ctx, list, createForm);
      const region = uniqueRegion(ctx);
      await fillField(page, scope, field(createForm, 'Name'), uniqueName(runId, 'cancelled'));
      await fillField(page, scope, field(createForm, 'Region'), region);
      await scope.getByRole('button', { name: 'Cancel', exact: true }).click();
      await scope.waitFor({ state: 'hidden' });
      if (new URL(page.url()).pathname !== list.path) throw new Error(`Cancel navigated to ${page.url()}`);
      const found = await api.get(`${API}?page=0&size=5&region=${encodeURIComponent(region)}`);
      if (found?.content?.length) throw new Error('Cancel created a price book');
    });

    await runCheck(ctx, 'price-books.create.server-error.duplicate-region', async () => {
      const scope = await openForm(ctx, list, createForm);
      await fillField(page, scope, field(createForm, 'Name'), uniqueName(runId, 'dup-region'));
      await fillField(page, scope, field(createForm, 'Region'), bookA.region);
      await scope.getByRole('button', { name: 'Create', exact: true }).click();
      await banner(scope).waitFor({ state: 'visible' });
      const text = squash(await banner(scope).innerText()).replace(/^error_outline\s*/, '');
      await expectSnackbar(page, 'A price book already exists for region');
      if (!text.startsWith(`A price book already exists for region ${bookA.region}`)) throw new Error(`banner reads "${text}"`);
      if (!(await scope.isVisible())) throw new Error('dialog closed although the create failed');
      return text;
    });

    await runCheck(ctx, 'price-books.create.server-error.date-range', async () => {
      const scope = await openForm(ctx, list, createForm);
      await fillField(page, scope, field(createForm, 'Name'), uniqueName(runId, 'bad-dates'));
      await fillField(page, scope, field(createForm, 'Region'), uniqueRegion(ctx));
      await scope.getByLabel('Effective date', { exact: true }).fill('2099-12-31');
      await scope.getByLabel('Expiration date', { exact: true }).fill('2099-01-01');
      await scope.getByRole('button', { name: 'Create', exact: true }).click();
      await banner(scope).waitFor({ state: 'visible' });
      const text = squash(await banner(scope).innerText()).replace(/^error_outline\s*/, '');
      await expectSnackbar(page, 'Expiration date must be on or after effective date');
      if (!text.startsWith('Expiration date must be on or after effective date')) throw new Error(`banner reads "${text}"`);
      return text;
    });

    // ── list behaviour ────────────────────────────────────────────────────────────────────────
    await runCheck(ctx, 'price-books.filter.region.button', async () => {
      await filterByRegion(page, bookZ.region);
      const rows = page.locator('tr.mat-mdc-row');
      await page.getByRole('row').filter({ hasText: bookZ.name }).first().waitFor({ state: 'visible' });
      await waitUntil(() => rows.count(), (n) => n === 1, { describe: (n) => `${n} rows shown for a unique region` });
      const text = squash(await rows.first().innerText());
      for (const cell of [bookZ.name, bookZ.region, 'EUR', 'NO']) {
        if (!text.includes(cell)) throw new Error(`row "${text}" lacks "${cell}"`);
      }
      return text;
    });

    await runCheck(ctx, 'price-books.filter.region.enter-key', async () => {
      await filterByRegion(page, bookA.region, { viaEnter: true });
      await page.getByRole('row').filter({ hasText: bookA.name }).first().waitFor({ state: 'visible' });
    });

    await runCheck(ctx, 'price-books.filter.clear-restores-list', async () => {
      await filterByRegion(page, bookA.region);
      await page.getByRole('row').filter({ hasText: bookA.name }).first().waitFor({ state: 'visible' });
      await page.getByRole('button', { name: 'Clear', exact: true }).click();
      await waitUntil(() => regionBox(page).inputValue(), (v) => v === '', { describe: (v) => `filter still holds "${v}"` });
      await page.getByRole('row').filter({ hasText: bookZ.name }).first().waitFor({ state: 'visible' });
      if (await page.getByRole('button', { name: 'Clear', exact: true }).count()) throw new Error('Clear is still shown with an empty filter');
    });

    await runCheck(ctx, 'price-books.filter.no-match-empty-state', async () => {
      await filterByRegion(page, `NONE-${runId}`);
      await page.getByText('No matching price books', { exact: true }).waitFor({ state: 'visible' });
      await page.getByText('Try another region filter.', { exact: true }).waitFor({ state: 'visible' });
    });

    // H-CPQ-PR-006: headers are rendered sortable, but the list never binds (sortChange).
    await runCheck(ctx, 'price-books.list.sort-header-reorders', async () => {
      await goto(page, list.path);
      await page.getByRole('row').filter({ hasText: bookZ.name }).first().waitFor({ state: 'visible' });
      const order = async () => (await page.locator('tr.mat-mdc-row').allInnerTexts())
        .filter((t) => t.includes(bookA.name) || t.includes(bookZ.name))
        .map((t) => (t.includes(bookA.name) ? 'A' : 'Z')).join('');
      const before = await order();
      let requests = 0;
      const count = (r) => { if (r.url().includes(API)) requests++; };
      page.on('request', count);
      const header = page.getByRole('columnheader', { name: 'Name', exact: true });
      await header.click();
      await header.click();
      const aria = await waitUntil(() => header.getAttribute('aria-sort'), (v) => v === 'descending', { timeoutMs: 2000 }).catch(() => 'not descending');
      await page.waitForTimeout(600);
      page.off('request', count);
      const after = await order();
      if (after === before && requests === 0) {
        throw defect(`"Name" header announces aria-sort=${aria}, but the rows stay ${before} and no list request is sent`);
      }
      return `rows ${before} -> ${after}, ${requests} request(s)`;
    }, { hypothesis: 'H-CPQ-PR-006' });

    await runCheck(ctx, 'price-books.row.click-opens-detail', async () => {
      await filterByRegion(page, bookA.region);
      await page.getByRole('row').filter({ hasText: bookA.name }).first().getByRole('cell').first().click();
      await page.waitForURL((url) => url.pathname === `/price-books/${bookA.id}`);
    }, tolerate);

    await runCheck(ctx, 'price-books.detail.not-found', async () => {
      await goto(page, '/price-books/999999999');
      await page.getByText('Price book not found', { exact: true }).waitFor({ state: 'visible' });
      await expectSnackbar(page, 'The requested resource was not found.').catch(() => {});
      await page.getByRole('button', { name: 'Back to price books', exact: true }).click();
      await page.waitForURL((url) => url.pathname === list.path);
    });

    // ── CRUD through the dialogs ──────────────────────────────────────────────────────────────
    const state = { id: null, name: uniqueName(runId, 'priceBook-ui'), region: uniqueRegion(ctx) };
    const editDialog = () => scopeLocator(page, editForm.scope);
    const openEdit = async () => {
      await openDetail(page, state.id, state.name);
      await page.locator('.edit-book-btn').click();
      await editDialog().waitFor({ state: 'visible' });
      return editDialog();
    };

    await runSteps(ctx, [
      {
        id: 'price-books.crud.create',
        ...tolerate,
        run: async () => {
          const scope = await openForm(ctx, list, createForm);
          await fillField(page, scope, field(createForm, 'Name'), `  ${state.name}  `); // the dialog trims
          await fillField(page, scope, field(createForm, 'Region'), state.region);
          await fillField(page, scope, field(createForm, 'Currency'), 'eur'); // the dialog upper-cases
          await scope.getByLabel('Effective date', { exact: true }).fill('2099-01-01');
          await scope.getByLabel('Expiration date', { exact: true }).fill('2099-06-30');
          await scope.getByRole('button', { name: 'Create', exact: true }).click();
          await page.waitForURL(/\/price-books\/(\d+)$/, { timeout: 15000 });
          state.id = page.url().match(/\/price-books\/(\d+)$/)[1];
          api.track('priceBook', state.id, `${API}/${state.id}`);
          return `${state.name} -> id ${state.id}`;
        },
      },
      {
        id: 'price-books.crud.persist.create',
        run: async () => {
          const dto = await api.get(`${API}/${state.id}`);
          const expected = { name: state.name, region: state.region, currency: 'EUR', effectiveDate: '2099-01-01', expirationDate: '2099-06-30', isDefault: false };
          const wrong = Object.entries(expected).filter(([k, v]) => dto[k] !== v).map(([k, v]) => `${k}: expected ${v}, API holds ${dto[k]}`);
          if (wrong.length) throw new Error(wrong.join('; '));
          return `${Object.keys(expected).length} fields verified through the API`;
        },
      },
      {
        id: 'price-books.crud.detail.values',
        ...tolerate,
        run: async () => {
          await openDetail(page, state.id, state.name);
          const expected = { Region: state.region, Currency: 'EUR', Default: 'No', Effective: '2099-01-01', Expires: '2099-06-30', Status: 'Draft (editable)' };
          for (const [label, value] of Object.entries(expected)) {
            const actual = squash(await detailValue(page, label).innerText());
            if (actual !== value) throw new Error(`${label} reads "${actual}", expected "${value}"`);
          }
          const subtitle = squash(await page.locator('.page-header-subtitle').innerText());
          if (subtitle !== `${state.region} · EUR`) throw new Error(`subtitle reads "${subtitle}"`);
        },
      },
      {
        id: 'price-books.crud.edit.prefilled',
        ...tolerate,
        run: async () => {
          const scope = await openEdit();
          const actual = {
            Name: await readField(scope, field(editForm, 'Name')),
            Region: await readField(scope, field(editForm, 'Region')),
            Currency: await readField(scope, field(editForm, 'Currency')),
            Effective: await scope.getByLabel('Effective date', { exact: true }).inputValue(),
            Expires: await scope.getByLabel('Expiration date', { exact: true }).inputValue(),
            Default: await readField(scope, field(editForm, 'Default price book')),
          };
          const expected = { Name: state.name, Region: state.region, Currency: 'EUR', Effective: '2099-01-01', Expires: '2099-06-30', Default: false };
          const wrong = Object.keys(expected).filter((k) => actual[k] !== expected[k]).map((k) => `${k}: "${actual[k]}"`);
          await scope.getByRole('button', { name: 'Cancel', exact: true }).click();
          if (wrong.length) throw new Error(`edit dialog not prefilled — ${wrong.join(', ')}`);
        },
      },
      {
        id: 'price-books.crud.edit.save',
        ...tolerate,
        run: async () => {
          const scope = await openEdit();
          const renamed = `${state.name}-edited`;
          await fillField(page, scope, field(editForm, 'Name'), renamed);
          await fillField(page, scope, field(editForm, 'Currency'), 'gbp');
          await scope.getByLabel('Expiration date', { exact: true }).fill('2099-12-31');
          await scope.getByRole('button', { name: 'Save', exact: true }).click();
          await scope.waitFor({ state: 'hidden' });
          state.name = renamed;
          await page.getByRole('heading', { level: 1 }).filter({ hasText: renamed }).waitFor({ state: 'visible' });
          await waitUntil(async () => squash(await detailValue(page, 'Currency').innerText()), (v) => v === 'GBP', {
            describe: (v) => `detail card still shows currency "${v}" after Save`,
          });
        },
      },
      {
        id: 'price-books.crud.persist.update',
        run: async () => {
          const dto = await api.get(`${API}/${state.id}`);
          const expected = { name: state.name, region: state.region, currency: 'GBP', effectiveDate: '2099-01-01', expirationDate: '2099-12-31' };
          const wrong = Object.entries(expected).filter(([k, v]) => dto[k] !== v).map(([k, v]) => `${k}: expected ${v}, API holds ${dto[k]}`);
          if (wrong.length) throw new Error(wrong.join('; '));
        },
      },
      {
        // H-CPQ-PR-012: the dialog omits emptied fields from the PUT and the backend mapper ignores
        // nulls, so an optional value, once set, can never be cleared again.
        id: 'price-books.crud.edit.clear-optional-field',
        hypothesis: 'H-CPQ-PR-012',
        continueOnFail: true,
        ...tolerate,
        run: async () => {
          const scope = await openEdit();
          await fillField(page, scope, field(editForm, 'Region'), '');
          await scope.getByLabel('Expiration date', { exact: true }).fill('');
          await scope.getByRole('button', { name: 'Save', exact: true }).click();
          await scope.waitFor({ state: 'hidden' });
          const dto = await api.get(`${API}/${state.id}`);
          const kept = [];
          if (dto.region) kept.push(`region="${dto.region}"`);
          if (dto.expirationDate) kept.push(`expirationDate=${dto.expirationDate}`);
          if (kept.length) throw defect(`Save reported success after clearing Region and Expiration date, but the API still holds ${kept.join(', ')}`);
          return 'cleared values persisted as null';
        },
      },
      {
        id: 'price-books.crud.delete.cancel-keeps-record',
        ...tolerate,
        run: async () => {
          await openDetail(page, state.id, state.name);
          await page.locator('.delete-book-btn').click();
          const action = detailFor(state.id).actions.find((a) => a.name === 'Delete');
          await confirmMatDialog(page, { title: action.confirm.title, message: action.confirm.message, click: 'Cancel' });
          await api.get(`${API}/${state.id}`);
          if (new URL(page.url()).pathname !== `/price-books/${state.id}`) throw new Error('Cancel navigated away');
        },
      },
      {
        id: 'price-books.crud.delete',
        ...tolerate,
        run: async () => {
          await openDetail(page, state.id, state.name);
          await page.locator('.delete-book-btn').click();
          await confirmMatDialog(page, { title: 'Delete price book', message: `Soft-delete "${state.name}"?`, click: 'Delete' });
          await page.waitForURL((url) => url.pathname === list.path, { timeout: 15000 });
          const proof = await expectGone(api, `${API}/${state.id}`);
          api.untrack('priceBook', state.id);
          return proof;
        },
      },
    ]);

    // ── detail structure + entries ────────────────────────────────────────────────────────────
    const detail = detailFor(bookA.id);
    await runShell(ctx, detail, { heading: bookA.name });
    await runFieldContract(tolerantCtx(ctx), detail, detail.forms[0]);

    // H-CPQ-PR-013 as the user meets it: every detail page opens with a red error banner.
    await runCheck(ctx, 'price-books.detail.loads-without-error', async () => {
      await goto(page, detail.path);
      await page.getByRole('heading', { level: 1 }).filter({ hasText: bookA.name }).waitFor({ state: 'visible' });
      const alert = page.locator('.error-banner[role="alert"]');
      const shown = await alert.waitFor({ state: 'visible', timeout: 2500 }).then(() => true).catch(() => false);
      await dismissSnackbars(page);
      if (shown) throw defect(`detail page opens with the error banner "${squash(await alert.innerText()).replace(/^error_outline\s*/, '').replace(/\s*Dismiss$/, '')}"`);
    }, { hypothesis: 'H-CPQ-PR-013', ...tolerate });

    const entry = { product: `UAT-${runId}-SKU`, id: null };
    const entryForm = detail.forms[0];
    const entryDialog = (title) => scopeLocator(page, { kind: 'dialog', title });
    const entryRow = () => page.locator('.entries-table tbody tr').filter({ hasText: entry.product }).first();
    const entriesOf = async () => (await api.get(`${API}/${bookA.id}/entries?page=0&size=50`))?.content ?? [];
    const openEntryDialog = async () => {
      await openDetail(page, bookA.id, bookA.name);
      await page.locator('.add-entry-btn').click();
      await entryDialog('Add entry').waitFor({ state: 'visible' });
      return entryDialog('Add entry');
    };

    await runSteps(ctx, [
      {
        id: 'price-books.entries.api-healthy',
        hypothesis: 'H-CPQ-PR-013',
        run: async () => {
          try {
            const found = await api.get(`${API}/${bookA.id}/entries?page=0&size=5`);
            return `GET entries -> ${found?.content?.length ?? 0} rows`;
          } catch (error) {
            if (error instanceof ApiCallError && error.status >= 500) throw defect(error.message);
            throw error;
          }
        },
      },
      {
        id: 'price-books.entries.empty-state',
        run: async () => {
          await openDetail(page, bookA.id, bookA.name);
          await page.getByText('No entries', { exact: true }).waitFor({ state: 'visible' });
          await page.getByText('This price book has no product entries yet.', { exact: true }).waitFor({ state: 'visible' });
        },
      },
      {
        id: 'price-books.entries.create',
        run: async () => {
          const scope = await openEntryDialog();
          const prefilled = await readField(scope, field(entryForm, 'Currency (optional)'));
          if (prefilled !== bookA.currency) throw new Error(`entry currency prefilled with "${prefilled}", expected the book currency ${bookA.currency}`);
          await fillField(page, scope, field(entryForm, 'Product external ID'), entry.product);
          await fillField(page, scope, field(entryForm, 'List price'), '100');
          await fillField(page, scope, field(entryForm, 'Floor price (optional)'), '80');
          await scope.getByRole('button', { name: 'Create', exact: true }).click();
          await scope.waitFor({ state: 'hidden' });
          await entryRow().waitFor({ state: 'visible' });
          const headers = (await page.locator('.entries-table thead th').allInnerTexts()).map(squash);
          if (JSON.stringify(headers) !== JSON.stringify(ENTRY_COLUMNS)) throw new Error(`entry columns are [${headers}]`);
          const row = squash(await entryRow().innerText());
          for (const cell of ['$100.00', '$80.00', bookA.currency]) if (!row.includes(cell)) throw new Error(`row "${row}" lacks ${cell}`);
          await page.locator('mat-paginator').waitFor({ state: 'visible' });
          const saved = (await entriesOf()).find((e) => e.productExternalId === entry.product);
          if (!saved) throw new Error('entry is in the table but not in the API');
          if (Number(saved.listPrice) !== 100 || Number(saved.floorPrice) !== 80) throw new Error(`API holds list ${saved.listPrice} / floor ${saved.floorPrice}`);
          entry.id = saved.id;
          return row;
        },
      },
      {
        id: 'price-books.entries.duplicate-product-rejected',
        continueOnFail: true,
        run: async () => {
          const scope = await openEntryDialog();
          await fillField(page, scope, field(entryForm, 'Product external ID'), entry.product);
          await fillField(page, scope, field(entryForm, 'List price'), '5');
          await scope.getByRole('button', { name: 'Create', exact: true }).click();
          await banner(scope).waitFor({ state: 'visible' });
          const text = squash(await banner(scope).innerText()).replace(/^error_outline\s*/, '');
          await expectSnackbar(page, `Product ${entry.product} already exists`);
          await scope.getByRole('button', { name: 'Cancel', exact: true }).click();
          if (!text.endsWith('[DUPLICATE_PRICE_BOOK_ENTRY]')) throw new Error(`banner reads "${text}"`);
          return text;
        },
      },
      {
        id: 'price-books.entries.floor-above-list-rejected',
        continueOnFail: true,
        run: async () => {
          const scope = await openEntryDialog();
          await fillField(page, scope, field(entryForm, 'Product external ID'), `${entry.product}-FLOOR`);
          await fillField(page, scope, field(entryForm, 'List price'), '10');
          await fillField(page, scope, field(entryForm, 'Floor price (optional)'), '11');
          await scope.getByRole('button', { name: 'Create', exact: true }).click();
          await banner(scope).waitFor({ state: 'visible' });
          const text = squash(await banner(scope).innerText()).replace(/^error_outline\s*/, '');
          await expectSnackbar(page, 'Floor price must be less than or equal to list price');
          await scope.getByRole('button', { name: 'Cancel', exact: true }).click();
          return text;
        },
      },
      {
        id: 'price-books.entries.edit',
        run: async () => {
          await openDetail(page, bookA.id, bookA.name);
          await entryRow().locator('.row-edit-btn').click();
          const scope = entryDialog('Edit entry');
          await scope.waitFor({ state: 'visible' });
          await fillField(page, scope, field(entryForm, 'List price'), '120');
          await scope.getByRole('button', { name: 'Save', exact: true }).click();
          await scope.waitFor({ state: 'hidden' });
          await waitUntil(async () => squash(await entryRow().innerText()), (t) => t.includes('$120.00'), { describe: (t) => `row still reads "${t}"` });
          const saved = (await entriesOf()).find((e) => e.id === entry.id);
          if (Number(saved?.listPrice) !== 120) throw new Error(`API holds list price ${saved?.listPrice}`);
        },
      },
      {
        id: 'price-books.entries.delete',
        run: async () => {
          await openDetail(page, bookA.id, bookA.name);
          await entryRow().locator('.row-delete-btn').click();
          await confirmMatDialog(page, { title: 'Delete entry', message: `Remove product "${entry.product}" from this price book?`, click: 'Cancel' });
          if (!(await entriesOf()).some((e) => e.id === entry.id)) throw new Error('Cancel deleted the entry');
          await entryRow().locator('.row-delete-btn').click();
          await confirmMatDialog(page, { title: 'Delete entry', click: 'Delete' });
          await entryRow().waitFor({ state: 'hidden' });
          if ((await entriesOf()).some((e) => e.id === entry.id)) throw new Error('entry is still in the API after delete');
        },
      },
    ]);

    await runPriceBookImmutability(ctx);
  },
};
