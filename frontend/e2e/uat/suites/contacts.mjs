/**
 * Contacts suite: shell + field contract + CRUD from the spec, plus hand-written checks for
 * search, row/menu click semantics, the two delete confirmations (Material dialog on the list,
 * native confirm on the detail page), conditional detail cards and the organization autocomplete.
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, defect, blocked, waitUntil } from '../../lib/health.mjs';
import { withNativeDialog, expectNoNativeDialog, confirmMatDialog } from '../../lib/dialogs.mjs';
import { settleOverlays, expectNoSnackbar } from '../../lib/overlays.mjs';
import { scopeLocator, controlLocator, fillField } from '../../lib/fields.mjs';
import { ApiCallError } from '../../lib/api.mjs';
import { ensure, uniqueName } from '../fixtures.mjs';
import { list, detail } from '../specs/contacts.mjs';
import { runShell } from '../runners/shell.mjs';
import { runFieldContract } from '../runners/fieldContract.mjs';
import { runCrud } from '../runners/crud.mjs';

const API = '/api/v1/contacts';
const DETAIL_URL = /\/contacts\/(\d+)$/;

/** Run-unique names end in a counter, so "…-lead-3" must not also match "…-lead-30". */
const named = (name) => new RegExp(`${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?!\\d)`);

/** The real <input>; the host <app-search-input> carries the same placeholder attribute. */
const searchBox = (page) => page.getByRole('textbox', { name: list.searchPlaceholder, exact: true });

/** Type a query into the list search box and wait for the search response. */
async function search(page, query) {
  await goto(page, list.path);
  const responded = page.waitForResponse((r) => r.url().includes(`${API}/search`) && r.ok(), { timeout: 10000 })
    .catch(() => null);
  await searchBox(page).fill(query);
  return responded;
}

/** Search by the run-unique last name and return the table row. */
async function findRow(page, lastName) {
  await search(page, lastName);
  const row = page.getByRole('row').filter({ hasText: named(lastName) }).first();
  await row.waitFor({ state: 'visible' });
  return row;
}

async function isGone(api, id) {
  try {
    await api.get(`${API}/${id}`);
    return false;
  } catch (error) {
    if (error instanceof ApiCallError && error.status === 404) return true;
    throw error;
  }
}

export default {
  id: 'contacts',
  backend: 'crm',

  async run(ctx) {
    const { page, api, runId } = ctx;
    const baseForm = list.forms[0];
    const field = (label) => baseForm.fields.find((f) => f.label === label);

    // The Organization autocomplete needs a real organization to pick.
    const org = await ensure(ctx, 'organization');
    const form = {
      ...baseForm,
      fields: baseForm.fields.map((f) => (f.label === 'Organization' ? { ...f, sample: org.name } : f)),
    };

    await runShell(ctx, list);
    await runFieldContract(ctx, list, baseForm);

    await runCrud(ctx, list, form, {
      entity: 'contact',
      apiPath: API,
      nameField: 'Last Name',
      detailUrl: DETAIL_URL,
      editPath: (id) => `/contacts/${id}/edit`,
      deleteAction: detail.actions.find((a) => a.name === 'Delete'),
    });

    // ── detail ────────────────────────────────────────────────────────────────
    const full = await ensure(ctx, 'contact', {
      organizationId: org.id, notes: 'Detail fixture notes', phone: '+1 555 0100',
      jobTitle: 'Detail Tester', department: 'QA',
    });
    const fullName = `${full.firstName} ${full.lastName}`;
    await runShell(ctx, detail, { path: `/contacts/${full.id}`, heading: fullName });

    await runCheck(ctx, 'contacts.detail.populated-cards', async () => {
      await goto(page, `/contacts/${full.id}`);
      await page.getByText('Detail Tester', { exact: true }).waitFor({ state: 'visible' }); // job-title subtitle
      const info = await scopeLocator(page, { kind: 'card', title: 'Contact Information' }).innerText();
      for (const value of [full.email, '+1 555 0100', 'QA']) {
        if (!info.includes(value)) throw new Error(`Contact Information card does not show "${value}"`);
      }
      await scopeLocator(page, { kind: 'card', title: 'Notes' }).getByText('Detail fixture notes').waitFor({ state: 'visible' });
      const link = scopeLocator(page, { kind: 'card', title: 'Organization' }).getByRole('link', { name: org.name });
      await link.click();
      await page.waitForURL((url) => url.pathname === `/organizations/${org.id}`);
      return `organization link -> /organizations/${org.id}`;
    });

    const bare = await ensure(ctx, 'contact');
    await runCheck(ctx, 'contacts.detail.empty-cards-hidden', async () => {
      await goto(page, `/contacts/${bare.id}`);
      const info = scopeLocator(page, { kind: 'card', title: 'Contact Information' });
      await info.waitFor({ state: 'visible' });
      for (const title of ['Organization', 'Notes']) {
        if (await scopeLocator(page, { kind: 'card', title }).isVisible()) {
          throw new Error(`"${title}" card is rendered for a contact that has none`);
        }
      }
      const text = (await info.innerText()).replace(/\s+/g, ' ');
      if (!/Phone\s*—/i.test(text) || !/Department\s*—/i.test(text)) {
        throw new Error(`empty Phone/Department should render "—", card says: ${text}`);
      }
    });

    await runCheck(ctx, 'contacts.detail.edit-opens-prefilled-form', async () => {
      await goto(page, `/contacts/${full.id}`);
      await page.getByRole('button', { name: 'Edit', exact: true }).click();
      await page.waitForURL((url) => url.pathname === `/contacts/${full.id}/edit`);
      const scope = scopeLocator(page, baseForm.scope);
      const expected = { 'Last Name': full.lastName, Email: full.email, Phone: '+1 555 0100', 'Job Title': 'Detail Tester', Department: 'QA', Notes: 'Detail fixture notes' };
      for (const [label, value] of Object.entries(expected)) {
        await waitUntil(() => controlLocator(scope, field(label)).inputValue(), (v) => v === value, {
          describe: (v) => `${label} holds "${v}", expected "${value}"`,
        });
      }
    });

    // H-CRM-033: the form patches the organization *name* (a string) into a control whose
    // displayWith expects an OrganizationDto, so `org?.name` is undefined and the input is blank.
    await runCheck(ctx, 'contacts.form.edit-shows-linked-organization', async () => {
      await goto(page, `/contacts/${full.id}/edit`);
      const scope = scopeLocator(page, baseForm.scope);
      await waitUntil(() => controlLocator(scope, field('Last Name')).inputValue(), (v) => v === full.lastName);
      const shown = await waitUntil(() => controlLocator(scope, field('Organization')).inputValue(), (v) => v === org.name, {
        timeoutMs: 2500, describe: (v) => v,
      }).catch((error) => error.message);
      if (shown !== org.name) {
        throw defect(`contact is linked to "${org.name}" but the edit form's Organization input shows "${shown}"`);
      }
    }, { hypothesis: 'H-CRM-033' });

    await runCheck(ctx, 'contacts.detail.back-returns-to-list', async () => {
      await goto(page, `/contacts/${full.id}`);
      await page.getByRole('button', { name: 'Go back to contacts', exact: true }).click();
      await page.waitForURL((url) => url.pathname === list.path);
    });

    await runCheck(ctx, 'contacts.detail.delete-cancel-keeps-record', async () => {
      await goto(page, `/contacts/${bare.id}`);
      await withNativeDialog(page, { type: 'confirm', message: `Delete "${bare.firstName} ${bare.lastName}"?`, accept: false },
        () => page.getByRole('button', { name: 'Delete', exact: true }).click());
      if (await isGone(api, bare.id)) throw new Error('record disappeared although the confirm was cancelled');
      if (new URL(page.url()).pathname !== `/contacts/${bare.id}`) throw new Error('cancelled delete navigated away');
    });

    // ── list ──────────────────────────────────────────────────────────────────
    await runCheck(ctx, 'contacts.search.finds-by-last-name', async () => {
      const row = await findRow(page, full.lastName);
      const text = (await row.innerText()).replace(/\s+/g, ' ');
      for (const value of [fullName, full.email, org.name, 'Detail Tester']) {
        if (!text.includes(value)) throw new Error(`row does not show "${value}": ${text}`);
      }
      return text.slice(0, 100);
    });

    // UI-CRM-SLDS Phase 2: the list renders the shared DataTableComponent, not a raw mat-table.
    await runCheck(ctx, 'contacts.list.uses-shared-data-table', async () => {
      await findRow(page, full.lastName);
      await page.getByTestId('data-table').waitFor({ state: 'visible', timeout: 5000 });
    });

    await runCheck(ctx, 'contacts.list.empty-cells-render-dash', async () => {
      const row = await findRow(page, bare.lastName);
      const cells = (await row.getByRole('cell').allInnerTexts()).map((t) => t.trim());
      // Name, Email, Phone, Organization, Title, actions
      for (const index of [2, 3, 4]) {
        if (cells[index] !== '—') throw new Error(`cell ${index} renders "${cells[index]}", expected "—"`);
      }
    });

    // H-CRM-032: the backend matches firstName OR lastName separately, so the full name a user
    // reads in the Name column finds nothing.
    await runCheck(ctx, 'contacts.search.finds-by-full-name', async () => {
      await search(page, fullName);
      const row = page.getByRole('row').filter({ hasText: named(full.lastName) }).first();
      const found = await row.waitFor({ state: 'visible', timeout: 4000 }).then(() => true).catch(() => false);
      if (!found) throw defect(`searching the displayed name "${fullName}" finds nothing (last name alone does)`);
    }, { hypothesis: 'H-CRM-032' });

    await runCheck(ctx, 'contacts.search.clear-restores-list', async () => {
      await findRow(page, full.lastName);
      await page.getByRole('button', { name: 'Clear search', exact: true }).click();
      await waitUntil(() => searchBox(page).inputValue(), (value) => value === '', {
        describe: (value) => `search box still holds "${value}" after Clear`,
      });
    });

    await runCheck(ctx, 'contacts.list.sort-sends-sort-param', async () => {
      await goto(page, list.path);
      const requested = page.waitForRequest((r) => r.url().includes(`${API}?`) && /sort=email(,|%2C)asc/.test(r.url()), { timeout: 8000 })
        .catch(() => null);
      await page.getByRole('columnheader', { name: 'Email' }).click();
      const request = await requested;
      if (!request) throw new Error('clicking the Email header did not request sort=email,asc');
      return new URL(request.url()).search;
    });

    await runCheck(ctx, 'contacts.list.page-sizes', async () => {
      await goto(page, list.path);
      await page.locator('mat-paginator mat-form-field').click();
      const options = (await page.getByRole('option').allInnerTexts()).map((t) => t.trim());
      await page.keyboard.press('Escape');
      await settleOverlays(page);
      const expected = list.tables[0].pageSizes.map(String);
      if (JSON.stringify(options) !== JSON.stringify(expected)) throw new Error(`page sizes are [${options}], expected [${expected}]`);
    });

    await runCheck(ctx, 'contacts.row.click-opens-detail', async () => {
      const row = await findRow(page, full.lastName);
      await row.getByRole('cell').nth(1).click();
      await page.waitForURL((url) => url.pathname === `/contacts/${full.id}`);
    });

    // H-CRM-014: the actions cell has no stopPropagation, so opening the row menu also fires the
    // row click and navigates away.
    await runCheck(ctx, 'contacts.row.menu-does-not-navigate', async () => {
      const row = await findRow(page, full.lastName);
      await row.getByRole('button').last().click();
      await page.waitForTimeout(600); // give a stray navigation the chance to happen
      const path = new URL(page.url()).pathname;
      if (path !== list.path) throw defect(`opening the row menu navigated to ${path}`);
      await page.getByRole('menuitem', { name: 'Edit' }).waitFor({ state: 'visible', timeout: 3000 });
      await page.getByRole('menuitem', { name: 'Delete' }).waitFor({ state: 'visible', timeout: 3000 });
      await page.keyboard.press('Escape');
      await settleOverlays(page);
    }, { hypothesis: 'H-CRM-014' });

    // H-A11Y-002: the row menu trigger is an icon-only button with no accessible name.
    await runCheck(ctx, 'contacts.a11y.row-menu-has-name', async () => {
      const row = await findRow(page, full.lastName);
      const label = (await row.getByRole('button').last().getAttribute('aria-label')) ?? '';
      if (!label.trim()) throw defect('row menu trigger has no aria-label (screen readers announce only "button")');
      return `aria-label="${label}"`;
    }, { hypothesis: 'H-A11Y-002' });

    // The list deletes through the Material ConfirmDialog (the detail page uses window.confirm).
    // While H-CRM-014 holds, the menu cannot be used at all, so this is BLOCKED rather than FAIL.
    const victim = await ensure(ctx, 'contact');
    const openRowMenu = async () => {
      const row = await findRow(page, victim.lastName);
      await row.getByRole('button').last().click();
      await page.waitForTimeout(600);
      const path = new URL(page.url()).pathname;
      if (path !== list.path) throw blocked(`row menu is unusable — opening it navigates to ${path} (H-CRM-014)`);
    };
    await runCheck(ctx, 'contacts.list.delete-uses-mat-dialog-cancel', async () => {
      await openRowMenu();
      await expectNoNativeDialog(page, () => page.getByRole('menuitem', { name: 'Delete' }).click());
      await confirmMatDialog(page, { title: 'Delete contact', message: `Delete "${victim.firstName} ${victim.lastName}"?`, click: 'Cancel' });
      if (await isGone(api, victim.id)) throw new Error('record was deleted although the dialog was cancelled');
    });
    await runCheck(ctx, 'contacts.list.delete-uses-mat-dialog-confirm', async () => {
      await openRowMenu();
      await page.getByRole('menuitem', { name: 'Delete' }).click();
      await confirmMatDialog(page, { title: 'Delete contact', click: 'Delete' });
      await waitUntil(() => isGone(api, victim.id), Boolean, { timeoutMs: 8000, describe: () => 'record still readable after confirming delete' });
      api.untrack('contact', victim.id);
      await expectNoSnackbar(page);
    });

    // ── form behaviour ────────────────────────────────────────────────────────
    await runCheck(ctx, 'contacts.form.cancel-returns-to-list', async () => {
      await goto(page, '/contacts/new');
      const scope = scopeLocator(page, baseForm.scope);
      await fillField(page, scope, field('First Name'), 'Discarded');
      await scope.getByRole('button', { name: 'Cancel', exact: true }).click();
      await page.waitForURL((url) => url.pathname === list.path);
    });

    // H-CRM-022: text typed into the Organization autocomplete but never picked is dropped
    // without any warning — the contact saves with no organization.
    await runCheck(ctx, 'contacts.form.organization-typed-not-picked', async () => {
      const lastName = uniqueName(runId, 'contact');
      await goto(page, '/contacts/new');
      const scope = scopeLocator(page, baseForm.scope);
      await fillField(page, scope, field('First Name'), 'Typed');
      await fillField(page, scope, field('Last Name'), lastName);
      const responded = page.waitForResponse((r) => /organizations\/search/.test(r.url()), { timeout: 8000 }).catch(() => null);
      await controlLocator(scope, field('Organization')).fill(org.name);
      await responded;
      await page.keyboard.press('Escape'); // close the panel without choosing
      await settleOverlays(page);
      await scope.getByRole('button', { name: 'Create', exact: true }).click();
      await page.waitForURL(DETAIL_URL, { timeout: 15000 });
      const id = page.url().match(DETAIL_URL)[1];
      api.track('contact', id, `${API}/${id}`);
      const dto = await api.get(`${API}/${id}`);
      if (dto.organizationId == null) {
        throw defect(`typed "${org.name}" (an exact match) without picking it: saved with organizationId=null and no warning`);
      }
      return `organizationId=${dto.organizationId}`;
    }, { hypothesis: 'H-CRM-022' });

    // H-CRM-030 (gap): the app has no success toasts today. Asserted so a future change is noticed.
    await runCheck(ctx, 'contacts.create.no-success-snackbar', async () => {
      const lastName = uniqueName(runId, 'contact');
      await goto(page, '/contacts/new');
      const scope = scopeLocator(page, baseForm.scope);
      await fillField(page, scope, field('First Name'), 'Quiet');
      await fillField(page, scope, field('Last Name'), lastName);
      await scope.getByRole('button', { name: 'Create', exact: true }).click();
      await page.waitForURL(DETAIL_URL, { timeout: 15000 });
      const id = page.url().match(DETAIL_URL)[1];
      api.track('contact', id, `${API}/${id}`);
      await expectNoSnackbar(page);
      return 'no snackbar after create';
    });
  },
};
