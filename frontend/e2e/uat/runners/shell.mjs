/**
 * Shell runner: everything a page spec declares about static structure — heading, subtitle,
 * sidebar entry, header actions, search box, table columns, cards, empty state.
 *
 * Check ids: `<spec.id>.shell.<aspect>[.<name>]`
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck } from '../../lib/health.mjs';
import { scopeLocator } from '../../lib/fields.mjs';

const has = (expected, actual) =>
  expected instanceof RegExp ? expected.test(actual) : actual.includes(expected);

/**
 * @param {object} ctx
 * @param {import('../contract.mjs').PageSpec} spec
 * @param {object} [options]
 * @param {string} [options.path] Concrete path when spec.path holds `:id` placeholders.
 * @param {string|RegExp} [options.heading] Expected heading for data-driven pages.
 */
export async function runShell(ctx, spec, { path = spec.path, heading = spec.heading } = {}) {
  const { page } = ctx;
  const id = `${spec.id}.shell`;
  const opts = { expectedErrors: spec.expectedErrors };

  const loaded = await runCheck(ctx, `${id}.heading`, async () => {
    await goto(page, path);
    const h1 = page.getByRole('heading', { level: 1 }).first();
    await h1.waitFor({ state: 'visible' });
    const text = (await h1.innerText()).trim();
    if (!has(heading, text)) throw new Error(`h1 is "${text}", expected ${heading}`);
    return text;
  }, opts);
  if (!loaded) return false; // nothing else is meaningful on a page that did not load

  if (spec.subtitle) {
    await runCheck(ctx, `${id}.subtitle`, async () => {
      await page.getByText(spec.subtitle, { exact: true }).first().waitFor({ state: 'visible' });
    }, opts);
  }

  if (spec.sidebar) {
    await runCheck(ctx, `${id}.sidebar`, async () => {
      await goto(page, '/dashboard');
      await page.getByRole('navigation', { name: 'Main navigation' })
        .getByRole('link', { name: spec.sidebar, exact: true }).click();
      await page.waitForURL((url) => url.pathname === spec.path);
      return `sidebar "${spec.sidebar}" -> ${spec.path}`;
    }, opts);
  }

  for (const action of (spec.actions ?? []).filter((a) => a.in === 'header' && !a.enabledWhen && !a.visibleWhen)) {
    await runCheck(ctx, `${id}.action.${action.name}`, async () => {
      await goto(page, path);
      const button = action.css
        ? page.locator(action.css).first()
        : page.getByRole('button', { name: action.ariaLabel ?? action.name, exact: true }).first();
      await button.waitFor({ state: 'visible' });
      if (action.navigatesTo && !action.navigatesTo.includes(':')) {
        await button.click();
        await page.waitForURL((url) => url.pathname === action.navigatesTo);
        return `-> ${action.navigatesTo}`;
      }
    }, { ...opts, hypothesis: action.hypothesis });
  }

  if (spec.searchPlaceholder) {
    await runCheck(ctx, `${id}.search`, async () => {
      await goto(page, path);
      // Role lookup targets the real <input>; the custom-element host repeats the placeholder attribute.
      await page.getByRole('textbox', { name: spec.searchPlaceholder, exact: true }).waitFor({ state: 'visible' });
    }, opts);
  }

  for (const [index, table] of (spec.tables ?? []).entries()) {
    await runCheck(ctx, `${id}.table${index || ''}.columns`, async () => {
      await goto(page, path);
      const root = scopeLocator(page, table.scope);
      // An empty list legitimately renders the empty state instead of a table.
      const headers = root.getByRole('columnheader');
      const appeared = await headers.first().waitFor({ state: 'visible', timeout: 8000 }).then(() => true).catch(() => false);
      if (!appeared) {
        if (spec.emptyState && (await page.getByText(spec.emptyState.title, { exact: true }).isVisible().catch(() => false))) {
          return 'list is empty — empty state shown instead of the table';
        }
        throw new Error('no column headers rendered and no empty state shown');
      }
      const actual = (await headers.allInnerTexts()).map((t) => t.replace(/\s+/g, ' ').trim()).filter(Boolean);
      const missing = table.columns.filter((c) => !actual.includes(c));
      const extra = actual.filter((c) => !table.columns.includes(c));
      if (missing.length || extra.length) {
        throw new Error(`columns differ — missing [${missing}] unexpected [${extra}] (saw: ${actual.join(', ')})`);
      }
      return actual.join(', ');
    }, opts);
  }

  for (const card of spec.cards ?? []) {
    await runCheck(ctx, `${id}.card.${card.title}`, async () => {
      const locator = scopeLocator(page, { kind: 'card', title: card.title });
      const visible = await locator.waitFor({ state: 'visible', timeout: 5000 }).then(() => true).catch(() => false);
      if (!visible) {
        if (card.optional) return 'optional card not rendered for this record';
        throw new Error(`card "${card.title}" is not rendered`);
      }
      const body = await locator.innerText();
      const missing = (card.labels ?? []).filter((label) => !body.toLowerCase().includes(label.toLowerCase()));
      if (missing.length) throw new Error(`card "${card.title}" is missing labels: ${missing.join(', ')}`);
    }, opts);
  }

  return true;
}
