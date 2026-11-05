/**
 * Quote detail-page workflows: action gating, header edit, line items, Calculate, documents,
 * Clone / Cancel / Delete, and the status lifecycle (Submit -> Approve | Reject -> Send ->
 * Accept | Expire).
 *
 * Quote *states* are only ever reached through the UI. Fixtures (a DRAFT quote, a product, a line
 * item) are seeded through the API; nothing here forces a status.
 *
 * Submit runs a configuration preflight routed to configuration-engine. While that backend is
 * down (preflight key `configValidate`) the Submit step records BLOCKED and `runSteps` marks the
 * whole downstream chain BLOCKED — the steps are nevertheless complete and run the day it is up.
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, runSteps, defect, blocked, gap, waitUntil } from '../../lib/health.mjs';
import { scopeLocator, controlLocator, fillField, blurField, readField, fieldError, selectOptions,
  normalizeValue, readPath } from '../../lib/fields.mjs';
import { withNativeDialog, confirmMatDialog } from '../../lib/dialogs.mjs';
import { settleOverlays, expectSnackbar, expectNoSnackbar, dismissSnackbars } from '../../lib/overlays.mjs';
import { ApiCallError } from '../../lib/api.mjs';
import { blockedReason } from '../../lib/preflight.mjs';
import { ensure, uniqueName } from '../fixtures.mjs';
import { detail, STATUS_LABELS } from '../specs/quotes.mjs';

export const API = '/api/v1/quotes';
const PRICE_BOOKS = '/api/v1/price-books';

const action = (name) => detail.actions.find((a) => a.name === name);
const form = (id) => detail.forms.find((f) => f.id === id);
const card = (page, title) => scopeLocator(page, { kind: 'card', title });
const squash = (text) => (text ?? '').replace(/\s+/g, ' ').trim();
/** Match CurrencyFormatPipe (en-US + quote currency). Header edit may change currency (e.g. to GBP). */
const formatMoney = (amount, currency) =>
  Number(amount).toLocaleString('en-US', { style: 'currency', currency: currency || 'USD' });

/** Seed a private mutable regional book; never change the environment's default. */
async function ensureMutableDefaultPriceBook(ctx) {
  return ensure(ctx, 'priceBook', {
    name: uniqueName(ctx.runId, 'calc-book'),
    region: `UAT-${ctx.runId}`,
    currency: 'USD',
    isDefault: false,
  });
}

async function ensurePriceBookEntryForProduct(ctx, bookId, product) {
  const path = `${PRICE_BOOKS}/${bookId}/entries`;
  const entry = await ctx.api.post(path, {
    productExternalId: String(product.id),
    listPrice: product.listPrice ?? 25,
    currency: product.currency || 'USD',
  });
  if (entry?.id === undefined) throw new Error('price book entry was created without an id');
  ctx.api.track('priceBookEntry', entry.id, `${path}/${entry.id}`);
}

/** Page-level action banner — the header / line-item / document cards own separate banners. */
const actionBanner = (page) => page.locator('.error-banner[role="alert"]:not(mat-card .error-banner)').first();
const bannerText = async (locator) => squash(await locator.locator('span').first().innerText());

/**
 * `<input type="date">` has no ARIA textbox role, so the field driver's role lookup cannot find
 * it; date fields are located by label instead. (Harness change request.)
 */
const control = (scope, field) =>
  field.kind === 'date' ? scope.getByLabel(field.label, { exact: true }).first() : controlLocator(scope, field);

async function fill(page, scope, field, value) {
  if (field.kind === 'date') await control(scope, field).fill(String(value));
  else await fillField(page, scope, field, value);
}

export async function openDetail(page, id) {
  await goto(page, `/quotes/${id}`);
  await page.getByRole('heading', { level: 1 }).first().waitFor({ state: 'visible' });
  await page.locator('[data-testid="quote-more-actions"]').waitFor({ state: 'visible' });
}

const quoteDto = (api, id) => api.get(`${API}/${id}`);

/** One product per run; its numeric id is what the Submit configuration preflight parses. */
const products = new Map();
const priceBooks = new Map();
const pricingCustomers = new Map();
export async function productFor(ctx) {
  if (!products.has(ctx.runId)) {
    const product = await ensure(ctx, 'product');
    const book = priceBooks.get(ctx.runId) ?? await ensureMutableDefaultPriceBook(ctx);
    priceBooks.set(ctx.runId, book);
    await ensurePriceBookEntryForProduct(ctx, book.id, product);
    products.set(ctx.runId, product);
  }
  return products.get(ctx.runId);
}

/** A DRAFT quote with one line item whose productExternalId is a real numeric product id. */
export async function draftWithLine(ctx, overrides = {}) {
  const product = await productFor(ctx);
  if (!pricingCustomers.has(ctx.runId)) {
    pricingCustomers.set(ctx.runId, await ensure(ctx, 'pricingCustomer'));
  }
  const quote = await ensure(ctx, 'quote', {
    customerExternalId: pricingCustomers.get(ctx.runId).externalId,
    region: priceBooks.get(ctx.runId).region,
    ...overrides,
  });
  try {
    await ctx.api.post(`${API}/${quote.id}/line-items`, {
      productExternalId: String(product.id), productName: product.name, listPrice: 25, quantity: 2,
    });
  } catch (error) {
    // A quote without a line item cannot be calculated, submitted or meaningfully cloned. When the
    // backend cannot create one, that is evidence for BLOCKED — the defect itself is reported
    // once, by quotes.lines.add (H-CPQ-Q-010), not by every check that needs the fixture.
    if (error instanceof ApiCallError && error.status >= 500) {
      throw blocked(`line items cannot be created: POST ${API}/${quote.id}/line-items -> HTTP ${error.status} ${String(error.body).slice(0, 140)}`);
    }
    throw error;
  }
  return { quote, product };
}

async function openMenu(page) {
  await page.locator('[data-testid="quote-more-actions"]').click();
  await page.getByRole('menu').waitFor({ state: 'visible' });
}

async function closeMenu(page) {
  await page.keyboard.press('Escape');
  await settleOverlays(page);
}

/** Click a header button or a More-menu item described by an ActionSpec. */
async function trigger(page, spec) {
  if (spec.in === 'menu') {
    await openMenu(page);
    await page.getByRole('menuitem', { name: spec.name, exact: true }).click();
  } else {
    await page.getByRole('button', { name: spec.name, exact: true }).first().click();
  }
}

/** Wait until the API reports `want`, then until the detail badge shows its friendly label. */
async function expectStatus(ctx, id, want) {
  await waitUntil(async () => (await quoteDto(ctx.api, id)).status, (s) => s === want, {
    timeoutMs: 8000, intervalMs: 250, describe: (s) => `API status is ${s}, expected ${want}`,
  });
  const details = card(ctx.page, 'Quote details');
  await waitUntil(async () => squash(await details.innerText()), (t) => t.includes(STATUS_LABELS[want]), {
    timeoutMs: 5000, describe: (t) => `status badge does not show "${STATUS_LABELS[want]}" — card says "${t.slice(0, 120)}"`,
  });
  return `API ${want}, badge "${STATUS_LABELS[want]}"`;
}

/**
 * Run a status-changing action and surface what the UI says when the backend refuses it.
 * `act` performs the click(s); the POST it causes is awaited so a failure is reported with the
 * HTTP status *and* the banner the user sees, never as a bare timeout.
 */
async function transition(ctx, id, verb, want, act) {
  const { page } = ctx;
  const responded = page.waitForResponse(
    (r) => r.url().includes(`${API}/${id}/${verb}`) && r.request().method() === 'POST', { timeout: 15000 },
  ).catch(() => null);
  await act();
  const response = await responded;
  if (!response) throw new Error(`no POST ${API}/${id}/${verb} was sent`);
  if (!response.ok()) {
    const said = await actionBanner(page).waitFor({ state: 'visible', timeout: 4000 })
      .then(() => bannerText(actionBanner(page))).catch(() => '(no banner)');
    await dismissSnackbars(page);
    throw new Error(`${verb} -> HTTP ${response.status()}; banner: "${said}"`);
  }
  return expectStatus(ctx, id, want);
}

/* ------------------------------------------------------------------ action gating */

/**
 * The one generic gating check: for `status`, every ActionSpec with `enabledWhen` must be
 * enabled/disabled accordingly, and a disabled action must carry its `titleDisabled` tooltip
 * (an enabled one must not).
 */
export async function assertActionGating(ctx, id, status) {
  const { page } = ctx;
  await openDetail(page, id);
  const actual = (await quoteDto(ctx.api, id)).status;
  if (actual !== status) throw new Error(`quote ${id} is ${actual}, not ${status} — gating not evaluated`);

  const problems = [];
  const gated = detail.actions.filter((a) => a.enabledWhen);

  const inspect = async (spec, locator) => {
    const expectEnabled = spec.enabledWhen.includes(status);
    await locator.waitFor({ state: 'visible', timeout: 5000 });
    const enabled = await waitUntil(() => locator.isEnabled(), (v) => v === expectEnabled, { timeoutMs: 2000 })
      .catch(() => !expectEnabled);
    if (enabled !== expectEnabled) problems.push(`${spec.name}: ${enabled ? 'enabled' : 'disabled'}, expected ${expectEnabled ? 'enabled' : 'disabled'}`);
    if (spec.titleDisabled) {
      const title = (await locator.getAttribute('title')) ?? '';
      if (!expectEnabled && title !== spec.titleDisabled) problems.push(`${spec.name}: disabled tooltip is "${title}", expected "${spec.titleDisabled}"`);
      if (expectEnabled && (!title || title === spec.titleDisabled)) problems.push(`${spec.name}: enabled but tooltip is "${title}"`);
    }
  };

  for (const spec of gated.filter((a) => a.in === 'header')) {
    await inspect(spec, page.getByRole('button', { name: spec.name, exact: true }).first());
  }
  const trigger_ = page.getByRole('button', { name: action('Approve').menuTrigger, exact: true });
  if ((await trigger_.count()) !== 1) problems.push(`More menu trigger with aria-label "${action('Approve').menuTrigger}" not found`);
  await openMenu(page);
  for (const spec of gated.filter((a) => a.in === 'menu')) {
    await inspect(spec, page.getByRole('menuitem', { name: spec.name, exact: true }));
  }
  await closeMenu(page);

  if (problems.length) throw new Error(`[${status}] ${problems.join('; ')}`);
  return `[${status}] ${gated.length} actions gated as declared`;
}

/* ------------------------------------------------------------------ detail structure */

export async function runDetailStructure(ctx, quote) {
  const { page, api } = ctx;
  const id = quote.id;

  await runCheck(ctx, 'quotes.detail.actions.gating.DRAFT', () => assertActionGating(ctx, id, 'DRAFT'));

  await runCheck(ctx, 'quotes.detail.subtitle-is-quote-number', async () => {
    await openDetail(page, id);
    await page.getByText(quote.quoteNumber, { exact: true }).first().waitFor({ state: 'visible' });
    return quote.quoteNumber;
  });

  await runCheck(ctx, 'quotes.detail.details-card.values', async () => {
    await openDetail(page, id);
    const dto = await quoteDto(api, id);
    const text = squash(await card(page, 'Quote details').innerText());
    const expected = [STATUS_LABELS[dto.status], dto.dealType, dto.customerExternalId, `${dto.durationInMonths} months`];
    const missing = expected.filter((value) => !text.includes(String(value)));
    if (missing.length) throw new Error(`details card lacks ${missing.join(', ')} — card says "${text}"`);
    return expected.join(' · ');
  });

  await runCheck(ctx, 'quotes.detail.empty-texts', async () => {
    await openDetail(page, id);
    for (const spec of detail.cards.filter((c) => c.emptyText)) {
      await card(page, spec.title).getByText(spec.emptyText, { exact: true }).waitFor({ state: 'visible', timeout: 8000 });
    }
    await page.getByRole('note').filter({ hasText: 'Clone creates a new DRAFT' }).waitFor({ state: 'visible' });
    return detail.cards.filter((c) => c.emptyText).map((c) => `"${c.emptyText}"`).join(' · ');
  });

  await runCheck(ctx, 'quotes.detail.back-to-quotes', async () => {
    await openDetail(page, id);
    await trigger(page, action('Back to quotes'));
    await page.waitForURL((url) => url.pathname === '/quotes');
  });

  await runCheck(ctx, 'quotes.detail.unknown-id', async () => {
    await goto(page, '/quotes/999999999');
    const alert = page.getByRole('alert').first();
    await alert.waitFor({ state: 'visible' });
    const said = squash(await alert.innerText());
    await dismissSnackbars(page);
    for (const name of ['Retry', 'Back to quotes']) {
      await alert.getByRole('button', { name, exact: true }).waitFor({ state: 'visible' });
    }
    return said.slice(0, 120);
  });
}

/* ------------------------------------------------------------------ header edit */

export async function runHeaderEdit(ctx, quote) {
  const { page, api } = ctx;
  const spec = form('header');
  const id = quote.id;
  const scope = () => scopeLocator(page, spec.scope);
  const startEdit = async () => {
    await openDetail(page, id);
    await scope().getByRole('button', { name: spec.open.action, exact: true }).click();
    await control(scope(), spec.fields[0]).waitFor({ state: 'visible' });
  };

  await runCheck(ctx, 'quotes.header.read-only-hint', async () => {
    await openDetail(page, id);
    const hint = squash(await scope().locator('.hint').innerText());
    const want = 'Edit header fields (name, dates, region, notes). Customer, deal type, status, and totals are not editable here.';
    if (hint !== want) throw new Error(`hint is "${hint}"`);
  });

  await runCheck(ctx, 'quotes.header.fields-present-and-prefilled', async () => {
    await startEdit();
    const dto = await quoteDto(api, id);
    const problems = [];
    for (const field of spec.fields) {
      const locator = control(scope(), field);
      if (!(await locator.isVisible().catch(() => false))) { problems.push(`${field.label}: not found`); continue; }
      if (field.kind === 'date' && (await locator.getAttribute('type')) !== 'date') problems.push(`${field.label}: not <input type="date">`);
      if (field.placeholder && (await locator.getAttribute('placeholder')) !== field.placeholder) problems.push(`${field.label}: placeholder differs`);
      const shown = await locator.inputValue();
      const held = readPath(dto, field.persist.apiPath);
      const want = held == null ? '' : String(held).slice(0, field.kind === 'date' ? 10 : undefined);
      if (shown !== want) problems.push(`${field.label}: shows "${shown}", API holds "${want}"`);
    }
    if (problems.length) throw new Error(problems.join('; '));
    return `${spec.fields.length} fields`;
  });

  await runCheck(ctx, 'quotes.header.name-required', async () => {
    await startEdit();
    const field = spec.fields[0];
    await fill(page, scope(), field, '');
    await blurField(page, scope(), field);
    const shown = await fieldError(scope(), field);
    if (shown !== field.validators[0].expectError) throw new Error(`mat-error is "${shown}"`);
    if (await scope().getByRole('button', { name: spec.submit.name, exact: true }).isEnabled()) {
      throw new Error('Save stays enabled while Name is empty');
    }
    return `"${shown}"`;
  });

  await runCheck(ctx, 'quotes.header.cancel-discards', async () => {
    await startEdit();
    await fill(page, scope(), spec.fields[0], 'UAT discarded name');
    await scope().getByRole('button', { name: spec.cancel.name, exact: true }).click();
    await scope().getByRole('button', { name: spec.open.action, exact: true }).waitFor({ state: 'visible' });
    const dto = await quoteDto(api, id);
    if (dto.name !== quote.name) throw new Error(`Cancel persisted the name: "${dto.name}"`);
  });

  await runCheck(ctx, 'quotes.header.save-persists', async () => {
    await startEdit();
    const typed = {};
    const newName = `${quote.name}-edited`;
    await fill(page, scope(), spec.fields[0], newName);
    typed.Name = newName;
    for (const field of spec.fields.filter((f) => f.editSample !== undefined)) {
      await fill(page, scope(), field, field.editSample);
      typed[field.label] = field.editSample;
    }
    await scope().getByRole('button', { name: spec.submit.name, exact: true }).click();
    await scope().getByRole('button', { name: spec.open.action, exact: true }).waitFor({ state: 'visible', timeout: 10000 });

    const dto = await quoteDto(api, id);
    const problems = [];
    for (const field of spec.fields) {
      if (!(field.label in typed)) continue;
      const want = field.kind === 'number' ? Number(typed[field.label]) : normalizeValue(typed[field.label], field.normalize);
      const raw = readPath(dto, field.persist.apiPath);
      const held = field.kind === 'date' && raw ? String(raw).slice(0, 10) : raw;
      if (String(held ?? '') !== String(want)) problems.push(`${field.label}: typed "${want}", API holds "${held}"`);
    }
    if (problems.length) throw new Error(problems.join('; '));
    await page.getByRole('heading', { level: 1 }).filter({ hasText: newName }).waitFor({ state: 'visible' });
    quote.name = newName;
    return `${Object.keys(typed).length} fields verified through the API`;
  });

  // H-CPQ-Q-009: "Subscription type" is a free-text box, but the column carries a CHECK constraint
  // (SAAS | ONPREM | HYBRID) — anything else is refused with an opaque integrity error.
  await runCheck(ctx, 'quotes.header.subscription-type-free-text', async () => {
    await startEdit();
    const field = spec.fields.find((f) => f.label === 'Subscription type');
    await fill(page, scope(), field, 'ANNUAL');
    const responded = page.waitForResponse((r) => r.url().endsWith(`${API}/${id}`) && r.request().method() === 'PUT', { timeout: 15000 })
      .catch(() => null);
    await scope().getByRole('button', { name: spec.submit.name, exact: true }).click();
    const response = await responded;
    if (!response) throw new Error('Save sent no PUT');
    if (response.ok()) return `"ANNUAL" accepted (HTTP ${response.status()})`;
    const banner = scope().locator('.error-banner[role="alert"]');
    await banner.waitFor({ state: 'visible' });
    const said = await bannerText(banner);
    await dismissSnackbars(page);
    throw defect(`free-text Subscription type "ANNUAL" -> HTTP ${response.status()}: "${said}" — the input offers no options and the message names neither the field nor the allowed values (SAAS, ONPREM, HYBRID)`);
  }, { hypothesis: 'H-CPQ-Q-009', expectedErrors: [/5\d\d PUT \/api\/v1\/quotes\/\d+/] });

  // H-CPQ-Q-008: no success feedback after a mutation (the only toasts are error toasts).
  await runCheck(ctx, 'quotes.feedback.success-snackbar', async () => {
    await startEdit();
    await fill(page, scope(), form('header').fields.find((f) => f.label === 'Notes'), 'UAT feedback probe');
    await scope().getByRole('button', { name: spec.submit.name, exact: true }).click();
    await scope().getByRole('button', { name: spec.open.action, exact: true }).waitFor({ state: 'visible', timeout: 10000 });
    const appeared = await expectNoSnackbar(page, { windowMs: 2000 }).then(() => false, () => true);
    if (!appeared) throw gap('header Save succeeded silently: no snackbar and no inline confirmation — the panel just leaves edit mode');
    return 'a snackbar followed the successful save';
  }, { hypothesis: 'H-CPQ-Q-008' });

  // H-CPQ-Q-004: this panel formats server errors `message (CODE)`; every other quote surface
  // uses `message [CODE]`. The quote is removed underneath an open editor to get a real error.
  await runCheck(ctx, 'quotes.header.error-format', async () => {
    const victim = await ensure(ctx, 'quote');
    await goto(page, `/quotes/${victim.id}`);
    await scope().getByRole('button', { name: spec.open.action, exact: true }).click();
    await control(scope(), spec.fields[0]).waitFor({ state: 'visible' });
    await api.del(`${API}/${victim.id}`);
    api.untrack('quote', victim.id);

    await scope().getByRole('button', { name: spec.submit.name, exact: true }).click();
    const headerBanner = scope().locator('.error-banner[role="alert"]');
    await headerBanner.waitFor({ state: 'visible' });
    const headerSaid = await bannerText(headerBanner);
    await dismissSnackbars(page);

    await trigger(page, action('Calculate'));
    await actionBanner(page).waitFor({ state: 'visible' });
    const actionSaid = await bannerText(actionBanner(page));
    await dismissSnackbars(page);

    const round = /\s\([A-Z0-9_]+\)$/.test(headerSaid);
    const square = /\s\[[A-Z0-9_]+\]$/.test(actionSaid);
    if (round && square) throw defect(`header panel says "${headerSaid}" while the page action says "${actionSaid}" — two error-code formats on one page`);
    return `header: "${headerSaid}" · action: "${actionSaid}"`;
  }, { hypothesis: 'H-CPQ-Q-004' });
}

/* ------------------------------------------------------------------ line items */

export async function runLineItems(ctx, quote) {
  const { page, api } = ctx;
  const add = form('add-line');
  const edit = form('edit-line');
  const id = quote.id;
  const addScope = () => scopeLocator(page, add.scope);
  const addButton = () => addScope().getByRole('button', { name: add.submit.name, exact: true });
  const lines = async () => (await quoteDto(api, id)).lineItems ?? [];
  const state = { product: null, line: null };
  const row = () => scopeLocator(page, { kind: 'row', rowText: state.product.name, within: edit.scope.within });

  await runCheck(ctx, 'quotes.lines.add-form.defaults-and-gating', async () => {
    await openDetail(page, id);
    for (const field of add.fields) await control(addScope(), field).waitFor({ state: 'visible' });
    const quantity = add.fields.find((f) => f.label === 'Quantity');
    const shown = await readField(addScope(), quantity);
    if (String(shown) !== String(quantity.default)) throw new Error(`Quantity defaults to "${shown}", expected ${quantity.default}`);
    if (await addButton().isEnabled()) throw new Error('"Add line" is enabled on an empty form');
    return 'Quantity = 1, Add line disabled';
  });

  for (const field of add.fields.filter((f) => f.validators)) {
    await runCheck(ctx, `quotes.lines.add-form.${field.label}.required`, async () => {
      await openDetail(page, id);
      for (const other of add.fields.filter((f) => f.required && f !== field)) {
        await fill(page, addScope(), other, other.sample ?? '1');
      }
      await blurField(page, addScope(), field);
      const shown = await fieldError(addScope(), field);
      if (shown !== field.validators[0].expectError) throw new Error(`mat-error is ${shown === null ? 'absent' : `"${shown}"`}, expected "${field.validators[0].expectError}"`);
      if (await addButton().isEnabled()) throw new Error(`"Add line" stays enabled while ${field.label} is empty`);
      return `"${shown}"`;
    });
  }

  await runSteps(ctx, [
    {
      id: 'quotes.lines.add',
      hypothesis: 'H-CPQ-Q-010',
      expectedErrors: [/5\d\d POST \/api\/v1\/quotes\/\d+\/line-items/],
      run: async () => {
        state.product = await productFor(ctx);
        await openDetail(page, id);
        const values = { 'Product external ID': String(state.product.id), 'Product name': state.product.name };
        for (const field of add.fields) {
          const value = values[field.label] ?? field.sample;
          await fill(page, addScope(), field, value);
          values[field.label] = value;
        }
        const responded = page.waitForResponse(
          (r) => r.url().includes(`${API}/${id}/line-items`) && r.request().method() === 'POST', { timeout: 15000 }).catch(() => null);
        await addButton().click();
        const response = await responded;
        if (!response) throw new Error('"Add line" sent no POST');
        if (!response.ok()) {
          await actionBanner(page).waitFor({ state: 'visible', timeout: 5000 }).catch(() => {});
          const said = await bannerText(actionBanner(page)).catch(() => '(no banner)');
          const toast = await expectSnackbar(page, /./).catch(() => '(no snackbar)');
          const message = `"Add line" -> HTTP ${response.status()} for ${JSON.stringify(values)}; banner: "${said}" · snackbar: "${toast}"`;
          if (response.status() >= 500) throw defect(message);
          throw new Error(message);
        }
        await row().waitFor({ state: 'visible', timeout: 10000 });
        const [line] = await lines();
        if (!line) throw new Error('the row rendered but the API holds no line item');
        state.line = line;
        const problems = add.fields
          .filter((f) => String(readPath(line, f.persist.apiPath)) !== String(values[f.label]))
          .map((f) => `${f.label}: typed "${values[f.label]}", API holds "${readPath(line, f.persist.apiPath)}"`);
        if (problems.length) throw new Error(problems.join('; '));
        // The form must reset for the next line.
        await waitUntil(() => readField(addScope(), add.fields[0]), (v) => v === '', { describe: (v) => `Product external ID still "${v}" after add` });
        return `line ${line.id} for product ${state.product.id}`;
      },
    },
    {
      id: 'quotes.lines.table.columns-and-count',
      run: async () => {
        const table = detail.tables[0];
        const headers = (await scopeLocator(page, table.scope).getByRole('columnheader').allInnerTexts()).map(squash);
        if (JSON.stringify(headers.map((h) => h.toLowerCase())) !== JSON.stringify(table.columns.map((c) => c.toLowerCase()))) {
          throw new Error(`line columns are [${headers.join(', ')}]`);
        }
        const dto = await quoteDto(api, id);
        const money = formatMoney(25, dto.currency);
        const text = squash(await row().innerText());
        for (const want of ['3', money]) if (!text.includes(want)) throw new Error(`row lacks "${want}": "${text}"`);
        const details = squash(await card(page, 'Quote details').innerText());
        if (!/Line items 1\b/i.test(details)) throw new Error(`details card line count is wrong: "${details}"`);
        return text;
      },
    },
    {
      id: 'quotes.lines.edit.cancel-discards',
      run: async () => {
        // Scope by product id before Edit: once the Name cell is an <input>, hasText(productName)
        // no longer matches the row (input values are not element text).
        const editing = scopeLocator(page, { kind: 'row', rowText: String(state.product.id), within: edit.scope.within });
        await editing.getByRole('button', { name: edit.open.action, exact: true }).click();
        const name = edit.fields[0];
        await control(editing, name).waitFor({ state: 'visible' });
        await fill(page, editing, name, 'UAT discarded');
        await editing.getByRole('button', { name: edit.cancel.name, exact: true }).click();
        await editing.getByRole('button', { name: edit.open.action, exact: true }).waitFor({ state: 'visible' });
        const [line] = await lines();
        if (line.productName !== state.product.name) throw new Error(`Cancel persisted "${line.productName}"`);
      },
    },
    {
      id: 'quotes.lines.edit.save-persists',
      run: async () => {
        await row().getByRole('button', { name: edit.open.action, exact: true }).click();
        const editing = scopeLocator(page, { kind: 'row', rowText: String(state.product.id), within: edit.scope.within });
        await control(editing, edit.fields[0]).waitFor({ state: 'visible' });
        for (const field of edit.fields) await fill(page, editing, field, field.editSample);
        await editing.getByRole('button', { name: edit.submit.name, exact: true }).click();
        await editing.getByRole('button', { name: edit.open.action, exact: true }).waitFor({ state: 'visible', timeout: 10000 });
        const [line] = await lines();
        const problems = edit.fields
          .filter((f) => String(readPath(line, f.persist.apiPath)) !== String(f.editSample))
          .map((f) => `${f.label}: typed "${f.editSample}", API holds "${readPath(line, f.persist.apiPath)}"`);
        if (problems.length) throw new Error(problems.join('; '));
        const text = squash(await editing.innerText());
        const money = formatMoney(40, (await quoteDto(api, id)).currency);
        if (!text.includes('UAT line renamed') || !text.includes(money)) throw new Error(`row did not refresh: "${text}"`);
        return text;
      },
    },
    {
      id: 'quotes.lines.remove',
      run: async () => {
        const editing = scopeLocator(page, { kind: 'row', rowText: String(state.product.id), within: edit.scope.within });
        await editing.getByRole('button', { name: 'Remove', exact: true }).click();
        await card(page, 'Line items').getByText('No line items yet.', { exact: true }).waitFor({ state: 'visible', timeout: 10000 });
        const left = await lines();
        if (left.length) throw new Error(`API still holds ${left.length} line item(s)`);
        return 'removed without any confirmation dialog';
      },
    },
  ]);
}

/* ------------------------------------------------------------------ calculate */

export async function runCalculate(ctx) {
  const { page, api } = ctx;

  await runCheck(ctx, 'quotes.calculate.without-lines-is-refused', async () => {
    const quote = await ensure(ctx, 'quote');
    await openDetail(page, quote.id);
    await trigger(page, action('Calculate'));
    await actionBanner(page).waitFor({ state: 'visible' });
    const said = await bannerText(actionBanner(page));
    const toast = await expectSnackbar(page, /./);
    if (said !== 'Quote must have at least one line item [QUOTE_NO_LINE_ITEMS]') throw new Error(`banner says "${said}"`);
    await actionBanner(page).getByRole('button', { name: 'Dismiss', exact: true }).click();
    await actionBanner(page).waitFor({ state: 'hidden' });
    return `banner "${said}" · snackbar "${toast}"`;
  });

  await runCheck(ctx, 'quotes.calculate.draft-with-line', async () => {
    const why = blockedReason(ctx.preflight, 'pricingExecute');
    if (why) throw blocked(why);
    const { quote } = await draftWithLine(ctx);
    await openDetail(page, quote.id);
    const responded = page.waitForResponse((r) => r.url().includes(`${API}/${quote.id}/calculate`), { timeout: 30000 }).catch(() => null);
    await trigger(page, action('Calculate'));
    const response = await responded;
    if (!response) throw new Error('no calculate request was sent');
    if (!response.ok()) {
      const said = await actionBanner(page).waitFor({ state: 'visible', timeout: 5000 })
        .then(() => bannerText(actionBanner(page))).catch(() => '(no banner)');
      await dismissSnackbars(page);
      throw new Error(`Calculate -> HTTP ${response.status()}; banner: "${said}"`);
    }
    const dto = await quoteDto(api, quote.id);
    if (!(Number(dto.totalAmount) > 0)) throw new Error(`Calculate returned 2xx but totalAmount is ${dto.totalAmount}`);
    const shown = squash(await card(page, 'Quote details').innerText());
    const money = formatMoney(dto.totalAmount, dto.currency);
    await waitUntil(async () => squash(await card(page, 'Quote details').innerText()), (t) => t.includes(money), {
      timeoutMs: 5000, describe: () => `details card does not show ${money}: "${shown}"`,
    });
    return `totalAmount ${dto.totalAmount} ${dto.currency}, ARR ${dto.arr ?? '—'} (2 x 25 list)`;
  }, { expectedErrors: [/5\d\d POST \/api\/v1\/quotes\/\d+\/calculate/] });
}

/* ------------------------------------------------------------------ documents */

export async function runDocuments(ctx, quote) {
  const { page, api } = ctx;
  const spec = form('documents');
  const field = spec.fields[0];
  const scope = () => scopeLocator(page, spec.scope);
  const id = quote.id;

  await runCheck(ctx, 'quotes.documents.type-options', async () => {
    await openDetail(page, id);
    const selected = await readField(scope(), field);
    if (selected !== field.default) throw new Error(`default type is "${selected}", expected "${field.default}"`);
    const actual = await selectOptions(page, scope(), field);
    const expected = field.options.map((o) => o.label);
    if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`options are [${actual.join(' | ')}]`);
    return actual.join(' | ');
  });

  await runSteps(ctx, [
    {
      id: 'quotes.documents.generate',
      expectedErrors: [/5\d\d POST \/api\/v1\/quotes\/\d+\/documents/],
      run: async () => {
        await openDetail(page, id);
        await scope().getByText('No documents generated yet.', { exact: true }).waitFor({ state: 'visible' });
        await fillField(page, scope(), field, 'Order form');
        const responded = page.waitForResponse(
          (r) => r.url().includes(`${API}/${id}/documents/generate`), { timeout: 30000 }).catch(() => null);
        await scope().getByRole('button', { name: spec.submit.name, exact: true }).click();
        const response = await responded;
        if (!response) throw new Error('no generate request was sent');
        if (!response.ok()) {
          const banner = scope().locator('.error-banner[role="alert"]');
          const said = await banner.waitFor({ state: 'visible', timeout: 5000 }).then(() => bannerText(banner)).catch(() => '(no banner)');
          await dismissSnackbars(page);
          throw new Error(`Generate -> HTTP ${response.status()}; banner: "${said}"`);
        }
        const row = scope().getByRole('row').filter({ hasText: 'Order form' }).first();
        await row.waitFor({ state: 'visible', timeout: 10000 });
        const headers = (await scope().getByRole('columnheader').allInnerTexts()).map(squash);
        const want = detail.tables[1].columns;
        if (JSON.stringify(headers.map((h) => h.toLowerCase())) !== JSON.stringify(want.map((c) => c.toLowerCase()))) {
          throw new Error(`document columns are [${headers.join(', ')}]`);
        }
        const docs = await api.get(`${API}/${id}/documents`);
        if (docs.length !== 1 || docs[0].documentType !== 'ORDER_FORM') throw new Error(`API holds ${JSON.stringify(docs).slice(0, 200)}`);
        return squash(await row.innerText());
      },
    },
    {
      id: 'quotes.documents.download',
      run: async () => {
        const download = page.waitForEvent('download', { timeout: 15000 }).catch(() => null);
        await scope().getByRole('button', { name: 'Download', exact: true }).first().click();
        const file = await download;
        if (!file) {
          const banner = scope().locator('.error-banner[role="alert"]');
          const said = (await banner.isVisible().catch(() => false)) ? await bannerText(banner) : '(no banner)';
          throw new Error(`no download started; banner: "${said}"`);
        }
        const name = file.suggestedFilename();
        await file.delete().catch(() => {});
        if (!/\.pdf$/i.test(name)) throw new Error(`downloaded "${name}", expected a .pdf`);
        return name;
      },
    },
  ]);
}

/* ------------------------------------------------------------------ clone / cancel / delete */

export async function runCloneCancelDelete(ctx) {
  const { page, api } = ctx;

  await runCheck(ctx, 'quotes.clone.cancel-dialog-stays', async () => {
    const { quote } = await draftWithLine(ctx);
    await openDetail(page, quote.id);
    await trigger(page, action('Clone'));
    await confirmMatDialog(page, { ...action('Clone').confirm, click: action('Clone').confirm.cancelText });
    await page.waitForTimeout(400);
    if (new URL(page.url()).pathname !== `/quotes/${quote.id}`) throw new Error(`Cancel navigated to ${page.url()}`);
  });

  await runCheck(ctx, 'quotes.clone.creates-draft-copy', async () => {
    const { quote, product } = await draftWithLine(ctx, { region: 'EMEA' });
    await openDetail(page, quote.id);
    await trigger(page, action('Clone'));
    const spec = action('Clone');
    await confirmMatDialog(page, { title: spec.confirm.title, message: spec.confirm.message, click: spec.confirm.confirmText });
    await page.waitForURL((url) => spec.effect.navigate.test(url.pathname) && url.pathname !== `/quotes/${quote.id}`, { timeout: 15000 });
    const cloneId = page.url().match(spec.effect.navigate)[1];
    api.track('quote', cloneId, `${API}/${cloneId}`);
    const clone = await quoteDto(api, cloneId);
    const problems = [];
    if (clone.status !== 'DRAFT') problems.push(`clone status is ${clone.status}`);
    if (clone.region !== 'EMEA') problems.push(`region not copied (${clone.region})`);
    if (clone.customerExternalId !== quote.customerExternalId) problems.push('customer not copied');
    if ((clone.lineItems ?? []).length !== 1 || clone.lineItems[0].productExternalId !== String(product.id)) problems.push('line item not copied');
    if (clone.quoteNumber === quote.quoteNumber) problems.push('clone reuses the quote number');
    if (problems.length) throw new Error(problems.join('; '));
    await page.getByRole('heading', { level: 1 }).first().waitFor({ state: 'visible' });
    await card(page, 'Line items').getByRole('row').filter({ hasText: String(product.id) }).first().waitFor({ state: 'visible' });
    return `quote ${quote.id} -> clone ${cloneId} "${clone.name}" (${clone.quoteNumber})`;
  });

  const spec = action('Cancel quote');
  const cancelled = { id: null };
  await runSteps(ctx, [
    {
      id: 'quotes.cancel.keep-leaves-status',
      run: async () => {
        const quote = await ensure(ctx, 'quote');
        cancelled.id = quote.id;
        await openDetail(page, quote.id);
        await trigger(page, spec);
        await confirmMatDialog(page, { title: spec.confirm.title, message: spec.confirm.message, click: spec.confirm.cancelText });
        await page.waitForTimeout(400);
        const status = (await quoteDto(api, quote.id)).status;
        if (status !== 'DRAFT') throw new Error(`"Keep" changed the status to ${status}`);
      },
    },
    {
      id: 'quotes.cancel.confirm-cancels',
      run: () => transition(ctx, cancelled.id, 'cancel', 'CANCELLED', async () => {
        await trigger(page, spec);
        await confirmMatDialog(page, { title: spec.confirm.title, message: spec.confirm.message, click: spec.confirm.confirmText });
      }),
    },
    { id: 'quotes.detail.actions.gating.CANCELLED', run: () => assertActionGating(ctx, cancelled.id, 'CANCELLED') },
    {
      id: 'quotes.cancel.cancelled-is-read-only',
      run: async () => {
        const hint = squash(await card(page, 'Quote header').locator('.hint').innerText());
        if (hint !== 'Header edit is available only for DRAFT or REJECTED quotes.') throw new Error(`hint is "${hint}"`);
        if (await card(page, 'Quote header').getByRole('button', { name: 'Edit', exact: true }).count()) throw new Error('Edit is still offered');
        if (await page.locator('mat-card').filter({ hasText: 'Add line item' }).count()) throw new Error('"Add line item" card is still rendered');
      },
    },
  ]);

  const del = action('Delete');
  const doomed = { id: null };
  await runSteps(ctx, [
    {
      id: 'quotes.delete.cancel-keeps-record',
      run: async () => {
        const quote = await ensure(ctx, 'quote');
        doomed.id = quote.id;
        doomed.name = quote.name;
        await openDetail(page, quote.id);
        await trigger(page, del);
        await confirmMatDialog(page, { title: del.confirm.title, message: del.confirm.message, click: del.confirm.cancelText });
        await page.waitForTimeout(400);
        if ((await quoteDto(api, quote.id)).id !== quote.id) throw new Error('record vanished although the dialog was cancelled');
      },
    },
    {
      id: 'quotes.delete.confirm-removes',
      run: async () => {
        await trigger(page, del);
        await confirmMatDialog(page, { title: del.confirm.title, message: new RegExp(`Soft-delete "${doomed.name}"`), click: del.confirm.confirmText });
        await page.waitForURL((url) => url.pathname === del.effect.navigate, { timeout: 15000 });
        try {
          await quoteDto(api, doomed.id);
        } catch (error) {
          if (error instanceof ApiCallError && error.status === 404) {
            api.untrack('quote', doomed.id);
            return `GET ${API}/${doomed.id} -> 404`;
          }
          throw error;
        }
        throw new Error(`quote ${doomed.id} is still readable through the API after delete`);
      },
    },
  ]);
}

/* ------------------------------------------------------------------ status lifecycle */

/**
 * What the user sees when Submit fails because configuration-engine is down (H-CPQ-Q-006).
 * Only observable while that backend is actually down.
 */
export async function runSubmitOutage(ctx) {
  const { page, api } = ctx;

  await runCheck(ctx, 'quotes.submit.without-lines-is-refused', async () => {
    const quote = await ensure(ctx, 'quote');
    await openDetail(page, quote.id);
    await trigger(page, action('Submit'));
    await actionBanner(page).waitFor({ state: 'visible' });
    const said = await bannerText(actionBanner(page));
    await expectSnackbar(page, 'Quote must have at least one line item');
    if (said !== 'Quote must have at least one line item [QUOTE_NO_LINE_ITEMS]') throw new Error(`banner says "${said}"`);
    const status = (await quoteDto(api, quote.id)).status;
    if (status !== 'DRAFT') throw new Error(`refused Submit moved the quote to ${status}`);
    return `banner "${said}"`;
  });

  await runCheck(ctx, 'quotes.submit.config-engine-down.user-message', async () => {
    if (!blockedReason(ctx.preflight, 'configValidate')) throw blocked('configuration validate is up — the outage message cannot be observed');
    const { quote } = await draftWithLine(ctx);
    await openDetail(page, quote.id);
    const responded = page.waitForResponse((r) => r.url().includes(`${API}/${quote.id}/submit`), { timeout: 30000 }).catch(() => null);
    await trigger(page, action('Submit'));
    const response = await responded;
    if (!response) throw new Error('no submit request was sent');
    if (response.ok()) throw new Error(`Submit succeeded (HTTP ${response.status()}) although the configValidate probe is down`);
    await actionBanner(page).waitFor({ state: 'visible' });
    const said = await bannerText(actionBanner(page));
    const toast = await expectSnackbar(page, /./);
    const status = (await quoteDto(api, quote.id)).status;
    if (status !== 'DRAFT') throw new Error(`failed Submit moved the quote to ${status}`);
    if (!(await page.getByRole('button', { name: 'Submit', exact: true }).isEnabled())) throw new Error('Submit is not re-enabled after the failure');
    const raw = /Failed to call|I\/O error|Connection refused|https?:\/\/|localhost|Exception|\b5\d\d\b|<EOL>|\{"/i;
    if (raw.test(said) || raw.test(toast)) {
      throw defect(`HTTP ${response.status()} — banner: "${said}" · snackbar: "${toast}" (backend exception text shown to the user; nothing says "configuration check unavailable, try again later")`);
    }
    return `HTTP ${response.status()} — banner: "${said}" · snackbar: "${toast}"`;
  }, { hypothesis: 'H-CPQ-Q-006', expectedErrors: [/5\d\d POST \/api\/v1\/quotes\/\d+\/submit/] });
}

const simple = (ctx, state, name, verb, want) => ({
  id: `${state.base}.${verb}`,
  run: () => transition(ctx, state.id, verb, want, () => trigger(ctx.page, action(name))),
});

const gating = (ctx, state, status) => ({
  id: `${state.base}.gating.${status}`,
  run: () => assertActionGating(ctx, state.id, status),
});

/** Seed a DRAFT + line, then Submit. Submit is BLOCKED (not FAILED) while config validate is down. */
function opening(ctx, state) {
  return [
    {
      id: `${state.base}.seed`,
      run: async () => {
        const { quote } = await draftWithLine(ctx);
        state.id = quote.id;
        await openDetail(ctx.page, quote.id);
        return `quote ${quote.id} DRAFT with one line`;
      },
    },
    {
      id: `${state.base}.submit`,
      run: async () => {
        const why = blockedReason(ctx.preflight, 'configValidate');
        if (why) throw blocked(`${why} — Submit runs a configuration preflight through it`);
        return transition(ctx, state.id, 'submit', 'IN_REVIEW', () => trigger(ctx.page, action('Submit')));
      },
    },
  ];
}

export async function runLifecycle(ctx) {
  const { page, api } = ctx;

  // Path 1: DRAFT -> IN_REVIEW -> APPROVED -> SENT -> ACCEPTED (ACCEPTED quotes cannot be deleted:
  // this quote is expected teardown residue).
  const won = { base: 'quotes.lifecycle.accept', id: null };
  await runSteps(ctx, [
    ...opening(ctx, won),
    gating(ctx, won, 'IN_REVIEW'),
    simple(ctx, won, 'Approve', 'approve', 'APPROVED'),
    gating(ctx, won, 'APPROVED'),
    simple(ctx, won, 'Send', 'send', 'SENT'),
    gating(ctx, won, 'SENT'),
    simple(ctx, won, 'Accept', 'accept', 'ACCEPTED'),
    gating(ctx, won, 'ACCEPTED'),
    {
      id: `${won.base}.feedback.no-success-snackbar`,
      run: () => expectNoSnackbar(page),
    },
  ]);

  // Path 2: IN_REVIEW -> REJECTED through window.prompt.
  const lost = { base: 'quotes.lifecycle.reject', id: null, prompt: null };
  const reject = action('Reject');
  await runSteps(ctx, [
    ...opening(ctx, lost),
    {
      id: `${lost.base}.prompt-cancel-keeps-status`,
      run: async () => {
        await openMenu(page);
        await withNativeDialog(page, { type: 'prompt', message: reject.confirm.message, accept: false },
          () => page.getByRole('menuitem', { name: reject.name, exact: true }).click());
        await page.waitForTimeout(400);
        const status = (await quoteDto(api, lost.id)).status;
        if (status !== 'IN_REVIEW') throw new Error(`dismissing the prompt changed the status to ${status}`);
      },
    },
    {
      id: `${lost.base}.reject`,
      run: () => transition(ctx, lost.id, 'reject', 'REJECTED', async () => {
        await openMenu(page);
        lost.prompt = await withNativeDialog(page,
          { type: 'prompt', message: reject.confirm.message, accept: true, promptText: 'UAT rejection reason' },
          () => page.getByRole('menuitem', { name: reject.name, exact: true }).click());
      }),
    },
    {
      // H-CPQ-Q-005 (detail half): the reason is collected through a blocking native prompt while
      // every other confirmation on this page uses the Material dialog.
      id: 'quotes.reject.uses-native-prompt',
      hypothesis: 'H-CPQ-Q-005',
      continueOnFail: true,
      run: async () => {
        if (lost.prompt?.type === 'prompt') throw defect(`Reject collected its reason through window.prompt("${lost.prompt.message}")`);
      },
    },
    gating(ctx, lost, 'REJECTED'),
    {
      id: `${lost.base}.rejected-is-editable-again`,
      run: async () => {
        await card(page, 'Quote header').getByRole('button', { name: 'Edit', exact: true }).waitFor({ state: 'visible' });
        await card(page, 'Add line item').waitFor({ state: 'visible' });
      },
    },
  ]);

  // Path 3: ... -> SENT -> EXPIRED through the Material confirm ("Expire" / "Keep").
  const stale = { base: 'quotes.lifecycle.expire', id: null };
  const expire = action('Expire');
  await runSteps(ctx, [
    ...opening(ctx, stale),
    simple(ctx, stale, 'Approve', 'approve', 'APPROVED'),
    simple(ctx, stale, 'Send', 'send', 'SENT'),
    {
      id: `${stale.base}.keep-leaves-status`,
      run: async () => {
        await trigger(page, expire);
        await confirmMatDialog(page, { title: expire.confirm.title, message: expire.confirm.message, click: expire.confirm.cancelText });
        await page.waitForTimeout(400);
        const status = (await quoteDto(api, stale.id)).status;
        if (status !== 'SENT') throw new Error(`"Keep" changed the status to ${status}`);
      },
    },
    {
      id: `${stale.base}.expire`,
      run: () => transition(ctx, stale.id, 'expire', 'EXPIRED', async () => {
        await trigger(page, expire);
        await confirmMatDialog(page, { title: expire.confirm.title, message: expire.confirm.message, click: expire.confirm.confirmText });
      }),
    },
    gating(ctx, stale, 'EXPIRED'),
  ]);
}
