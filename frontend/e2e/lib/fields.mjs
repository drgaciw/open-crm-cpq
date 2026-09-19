/**
 * Field driver: the one place that knows how to locate, fill, read and validate each Angular
 * Material control kind. Runners stay declarative; every Material quirk lives here once.
 *
 * Quirks encoded below:
 *  - The same label can appear several times on one page (quote detail renders "List price"
 *    three times), so every lookup happens inside a scope locator, never on the whole page.
 *  - Clicking a mat-select's combobox directly is intercepted by its floating label; click the
 *    enclosing mat-form-field instead.
 *  - mat-error only renders once the control is touched: fill, blur, then assert.
 *  - Playwright's fill() throws on non-numeric text in type="number"; invalid samples for number
 *    fields must themselves be numeric strings.
 *  - Autocomplete text that is typed but not picked leaves the bound id null; picking the option
 *    is mandatory. Results arrive per keystroke with no debounce, so fill in one input event and
 *    wait for the matching search response before choosing.
 */
import { settleOverlays } from './overlays.mjs';

/** @typedef {import('../uat/contract.mjs').Scope} Scope */
/** @typedef {import('../uat/contract.mjs').FieldSpec} FieldSpec */

/**
 * Resolve a contract Scope to a Playwright locator.
 * @param {import('playwright').Page} page
 * @param {Scope} [scope]
 * @returns {import('playwright').Locator}
 */
export function scopeLocator(page, scope = { kind: 'page' }) {
  switch (scope.kind) {
    case 'page':
      return page.locator('main#main-content, main').first();
    case 'dialog':
      return page.getByRole('dialog').filter({ hasText: scope.title });
    case 'card':
      return page.locator('mat-card').filter({
        has: page.locator('mat-card-title, h2, h3').filter({ hasText: scope.title }),
      }).first();
    case 'row': {
      const root = scope.within ? scopeLocator(page, scope.within) : page.locator('body');
      return root.locator('tr, [role="row"]').filter({ hasText: scope.rowText }).first();
    }
    case 'css':
      return page.locator(scope.selector).first();
    default:
      throw new Error(`unknown scope kind '${scope.kind}'`);
  }
}

/** Playwright role for a field kind. */
const ROLE = {
  input: 'textbox',
  textarea: 'textbox',
  date: 'textbox',
  number: 'spinbutton',
  select: 'combobox',
  autocomplete: 'combobox',
  checkbox: 'checkbox',
};

/**
 * The control itself, found by role + accessible name inside the scope.
 * @param {import('playwright').Locator} scope
 * @param {FieldSpec} field
 */
export function controlLocator(scope, field) {
  const name = field.ariaLabel ?? field.label;
  return scope.getByRole(ROLE[field.kind], { name, exact: true }).first();
}

/** The mat-form-field wrapping a control — where mat-error and the select click target live. */
export function formFieldLocator(scope, field) {
  // The `has` locator is re-resolved *inside* each mat-form-field, so it must not carry the
  // scope prefix (a scope-rooted locator would look for <main> inside the form field).
  const inner = scope.page().getByRole(ROLE[field.kind], { name: field.ariaLabel ?? field.label, exact: true });
  return scope.locator('mat-form-field').filter({ has: inner }).first();
}

/**
 * Apply the spec's normalisation so UI-entered and API-returned values compare fairly
 * (the app trims names, upper-cases currency, and numbers arrive as numbers).
 */
export function normalizeValue(value, normalize) {
  if (value === null || value === undefined) return value;
  switch (normalize) {
    case 'trim':
      return String(value).trim();
    case 'upper':
      return String(value).trim().toUpperCase();
    case 'number':
      return value === '' ? null : Number(value);
    default:
      return value;
  }
}

/** Read a dotted path (`'a.b'`) out of an API object. */
export function readPath(object, path) {
  return path.split('.').reduce((acc, key) => (acc == null ? acc : acc[key]), object);
}

/**
 * Fill one field according to its kind.
 * @param {import('playwright').Page} page
 * @param {import('playwright').Locator} scope
 * @param {FieldSpec} field
 * @param {string|number|boolean} value For selects: the option *label*.
 */
export async function fillField(page, scope, field, value) {
  const control = controlLocator(scope, field);

  switch (field.kind) {
    case 'input':
    case 'textarea':
    case 'number':
    case 'date':
      await control.fill(String(value));
      return;

    case 'checkbox':
      if ((await control.isChecked()) !== Boolean(value)) await control.click();
      return;

    case 'select':
      await formFieldLocator(scope, field).click();
      await page.getByRole('option', { name: String(value), exact: true }).click();
      await settleOverlays(page);
      return;

    case 'autocomplete': {
      const responded = field.source?.searchUrl
        ? page.waitForResponse((r) => field.source.searchUrl.test(r.url()) && r.ok(), { timeout: 10000 }).catch(() => null)
        : null;
      await control.fill(String(value)); // one input event — no per-keystroke request storm
      if (responded) await responded;
      await page.getByRole('option').filter({ hasText: String(value) }).first().click();
      await settleOverlays(page);
      return;
    }

    default:
      throw new Error(`cannot fill field kind '${field.kind}'`);
  }
}

/** Move focus off the control so Angular marks it touched and renders any mat-error. */
export async function blurField(page, scope, field) {
  const control = controlLocator(scope, field);
  if (field.kind === 'select') {
    // Opening and escaping a select is what marks it touched.
    await formFieldLocator(scope, field).click();
    await page.keyboard.press('Escape');
    await settleOverlays(page);
  } else {
    await control.focus();
  }
  await page.keyboard.press('Tab');
}

/** Current UI value: input text, select trigger text, or checkbox state. */
export async function readField(scope, field) {
  const control = controlLocator(scope, field);
  if (field.kind === 'checkbox') return control.isChecked();
  if (field.kind === 'select') {
    const trigger = formFieldLocator(scope, field).locator('.mat-mdc-select-value');
    return ((await trigger.textContent()) ?? '').replace(/\s+/g, ' ').trim();
  }
  return control.inputValue();
}

/**
 * Visible mat-error text for a field, or null when none is rendered.
 * Short timeout: absence is a legitimate, asserted outcome.
 */
export async function fieldError(scope, field, { timeoutMs = 1200 } = {}) {
  const error = formFieldLocator(scope, field).locator('mat-error').first();
  const shown = await error.waitFor({ state: 'visible', timeout: timeoutMs }).then(() => true).catch(() => false);
  return shown ? (await error.innerText()).trim() : null;
}

/** All option labels of a select, read from the open panel; closes it again. */
export async function selectOptions(page, scope, field) {
  await formFieldLocator(scope, field).click();
  const options = page.getByRole('option');
  await options.first().waitFor({ state: 'visible' });
  const labels = (await options.allInnerTexts()).map((t) => t.replace(/\s+/g, ' ').trim());
  await page.keyboard.press('Escape');
  await settleOverlays(page);
  return labels;
}
