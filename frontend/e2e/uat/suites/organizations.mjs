/**
 * Organizations suite: shell + field contract + CRUD from the spec, plus hand-written checks for
 * behaviour a declarative spec cannot express (search, row-menu click semantics, a11y names).
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, defect, waitUntil } from '../../lib/health.mjs';
import { withNativeDialog } from '../../lib/dialogs.mjs';
import { settleOverlays } from '../../lib/overlays.mjs';
import { ensure } from '../fixtures.mjs';
import { list, detail } from '../specs/organizations.mjs';
import { runShell } from '../runners/shell.mjs';
import { runFieldContract } from '../runners/fieldContract.mjs';
import { runCrud } from '../runners/crud.mjs';

const API = '/api/v1/organizations';

/** The real <input>; the host <app-search-input> carries the same placeholder attribute. */
const searchBox = (page) => page.getByRole('textbox', { name: list.searchPlaceholder, exact: true });

/** Search for a record by its run-unique name and return its table row. */
async function findRow(page, name) {
  await goto(page, list.path);
  const responded = page.waitForResponse((r) => r.url().includes(`${API}/search`) && r.ok(), { timeout: 10000 })
    .catch(() => null); // never reject unhandled if fill() throws first
  await searchBox(page).fill(name);
  await responded;
  const row = page.getByRole('row').filter({ hasText: name }).first();
  await row.waitFor({ state: 'visible' });
  return row;
}

export default {
  id: 'organizations',
  backend: 'crm',

  async run(ctx) {
    const { page, api } = ctx;
    const form = list.forms[0];

    await runShell(ctx, list);
    await runFieldContract(ctx, list, form);

    await runCrud(ctx, list, form, {
      entity: 'organization',
      apiPath: API,
      nameField: 'Account Name',
      detailUrl: /\/organizations\/(\d+)$/,
      editPath: (id) => `/organizations/${id}/edit`,
      deleteAction: detail.actions.find((a) => a.name === 'Archive'),
    });

    // Detail-page structure needs a record that has every optional card populated.
    const org = await ensure(ctx, 'organization', { description: 'Detail fixture', phone: '+1 555 0100' });
    await runShell(ctx, detail, { path: `/organizations/${org.id}`, heading: org.name });

    await runCheck(ctx, 'organizations.search.finds-by-name', async () => {
      const row = await findRow(page, org.name);
      return (await row.innerText()).replace(/\s+/g, ' ').slice(0, 80);
    });

    // UI-CRM-SLDS Phase 2: the list renders the shared DataTableComponent, not a raw mat-table.
    await runCheck(ctx, 'organizations.list.uses-shared-data-table', async () => {
      await findRow(page, org.name);
      await page.getByTestId('data-table').waitFor({ state: 'visible', timeout: 5000 });
    });

    await runCheck(ctx, 'organizations.search.clear-restores-list', async () => {
      await findRow(page, org.name);
      await page.getByRole('button', { name: 'Clear search', exact: true }).click();
      await waitUntil(() => searchBox(page).inputValue(), (value) => value === '', {
        describe: (value) => `search box still holds "${value}" after Clear`,
      });
    });

    await runCheck(ctx, 'organizations.row.click-opens-detail', async () => {
      const row = await findRow(page, org.name);
      await row.getByRole('cell').first().click();
      await page.waitForURL((url) => url.pathname === `/organizations/${org.id}`);
    });

    // H-CRM-007: the actions cell has no stopPropagation, so opening the row menu also fires the
    // row click and navigates away — the menu the user asked for is never usable.
    await runCheck(ctx, 'organizations.row.menu-does-not-navigate', async () => {
      const row = await findRow(page, org.name);
      await row.getByRole('button').last().click();
      await page.waitForTimeout(600); // give a stray navigation the chance to happen
      const path = new URL(page.url()).pathname;
      if (path !== list.path) throw defect(`opening the row menu navigated to ${path}`);
      await page.getByRole('menuitem', { name: 'Edit' }).waitFor({ state: 'visible', timeout: 3000 });
      await page.keyboard.press('Escape');
      await settleOverlays(page);
    }, { hypothesis: 'H-CRM-007' });

    // H-A11Y-001: the row menu trigger is an icon-only button with no accessible name.
    await runCheck(ctx, 'organizations.a11y.row-menu-has-name', async () => {
      const row = await findRow(page, org.name);
      const trigger = row.getByRole('button').last();
      const label = (await trigger.getAttribute('aria-label')) ?? '';
      if (!label.trim()) throw defect('row menu trigger has no aria-label (screen readers announce only "button")');
      return `aria-label="${label}"`;
    }, { hypothesis: 'H-A11Y-001' });

    // The list page deletes through window.confirm with its own copy — covered separately from
    // the detail-page delete exercised by the CRUD runner.
    await runCheck(ctx, 'organizations.list.delete-cancel-keeps-record', async () => {
      const victim = await ensure(ctx, 'organization');
      await goto(page, `/organizations/${victim.id}`);
      await withNativeDialog(page, { type: 'confirm', message: /^Archive ".+"\? The account will be hidden from lists\.$/, accept: false },
        () => page.getByRole('button', { name: 'Archive', exact: true }).click());
      const still = await api.get(`${API}/${victim.id}`);
      if (still?.id !== victim.id) throw new Error('record disappeared although the confirm was cancelled');
    });
  },
};
