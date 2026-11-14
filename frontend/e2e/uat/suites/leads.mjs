/**
 * Leads suite: shell + field contract + CRUD from the spec, the lifecycle workflow, and
 * hand-written checks for status tabs, the priority filter, per-status row menus, the list's
 * Material confirmations, autocomplete pick/clear behaviour and the registered hypotheses.
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, defect, waitUntil } from '../../lib/health.mjs';
import { expectNoNativeDialog, confirmMatDialog, withNativeDialog } from '../../lib/dialogs.mjs';
import { settleOverlays, expectNoSnackbar, dismissSnackbars } from '../../lib/overlays.mjs';
import { scopeLocator, controlLocator, formFieldLocator, fillField, readField, selectOptions } from '../../lib/fields.mjs';
import { ApiCallError } from '../../lib/api.mjs';
import { ensure, uniqueName } from '../fixtures.mjs';
import { list, detail, STATUS_TABS, priorityFilter } from '../specs/leads.mjs';
import { runShell } from '../runners/shell.mjs';
import { runFieldContract } from '../runners/fieldContract.mjs';
import { runCrud } from '../runners/crud.mjs';
import { runLeadLifecycle, openLead, expectApiStatus } from '../workflows/lead-lifecycle.mjs';

const API = '/api/v1/leads';
const DETAIL_URL = /\/leads\/(\d+)$/;

/** Run-unique names end in a counter, so "…-lead-3" must not also match "…-lead-30". */
const named = (name) => new RegExp(`${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?!\\d)`);

const searchBox = (page) => page.getByRole('textbox', { name: list.searchPlaceholder, exact: true });

/** Search by the run-unique title and return the table row. */
async function findRow(page, title) {
  await goto(page, list.path);
  const responded = page.waitForResponse((r) => r.url().includes(`${API}/search`) && r.ok(), { timeout: 10000 })
    .catch(() => null);
  await searchBox(page).fill(title);
  await responded;
  const row = page.getByRole('row').filter({ hasText: named(title) }).first();
  await row.waitFor({ state: 'visible' });
  return row;
}

/** Open the row menu of a lead and return the visible menu item labels. */
async function openRowMenu(page, title) {
  const row = await findRow(page, title);
  await row.getByRole('button', { name: `Actions for ${title}`, exact: true }).click();
  await page.waitForTimeout(500); // menu animation; an empty menu never shows an item to wait for
  const items = page.getByRole('menuitem');
  return (await items.allInnerTexts()).map((t) => t.replace(/^[a-z_]+\s+/, '').replace(/\s+/g, ' ').trim());
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

/** Drive a lead to a status through the API. A converted lead's deal is ledgered for teardown. */
async function leadIn(ctx, status, overrides = {}) {
  const lead = await ensure(ctx, 'lead', overrides);
  const step = (action, body = {}) => ctx.api.post(`${API}/${lead.id}/${action}`, body);
  if (status === 'NEW') return lead;
  if (status === 'LOST') return step('lose', { reason: 'UAT fixture' });
  await step('contact');
  if (status === 'CONTACTED') return ctx.api.get(`${API}/${lead.id}`);
  await step('qualify');
  if (status === 'QUALIFIED') return ctx.api.get(`${API}/${lead.id}`);
  const converted = await step('convert');
  if (converted?.convertedDealId != null) {
    ctx.api.track('deal', converted.convertedDealId, `/api/v1/deals/${converted.convertedDealId}`);
  }
  return converted;
}

export default {
  id: 'leads',
  backend: 'crm',

  async run(ctx) {
    const { page, api, runId } = ctx;
    const baseForm = list.forms[0];
    const field = (label) => baseForm.fields.find((f) => f.label === label);
    const formScope = () => scopeLocator(page, baseForm.scope);

    const org = await ensure(ctx, 'organization');
    const contact = await ensure(ctx, 'contact');
    const contactName = `${contact.firstName} ${contact.lastName}`;
    const form = {
      ...baseForm,
      fields: baseForm.fields.map((f) => (f.label === 'Organization' ? { ...f, sample: org.name } : f)),
    };

    await runShell(ctx, list);
    await runFieldContract(ctx, list, baseForm);

    await runCrud(ctx, list, form, {
      entity: 'lead',
      apiPath: API,
      nameField: 'Title',
      detailUrl: DETAIL_URL,
      editPath: (id) => `/leads/${id}/edit`,
      deleteAction: detail.actions.find((a) => a.name === 'Delete'),
    });

    // ── detail ────────────────────────────────────────────────────────────────
    const full = await ensure(ctx, 'lead', {
      contactId: contact.id, organizationId: org.id, description: 'Detail fixture description',
      source: 'Webinar', priority: 'HIGH', estimatedValue: 1500,
    });
    await runShell(ctx, detail, { path: `/leads/${full.id}`, heading: full.title });

    await runCheck(ctx, 'leads.detail.populated-cards', async () => {
      await openLead(page, full, 'NEW');
      const info = (await scopeLocator(page, { kind: 'card', title: 'Lead Information' }).innerText()).replace(/\s+/g, ' ');
      for (const value of ['Webinar', '$1,500.00']) {
        if (!info.includes(value)) throw new Error(`Lead Information card does not show "${value}": ${info}`);
      }
      await scopeLocator(page, { kind: 'card', title: 'Description' }).getByText('Detail fixture description').waitFor({ state: 'visible' });
      await scopeLocator(page, { kind: 'card', title: 'Organization' }).getByRole('link', { name: org.name }).waitFor({ state: 'visible' });
      await scopeLocator(page, { kind: 'card', title: 'Contact' }).getByRole('link', { name: contactName }).click();
      await page.waitForURL((url) => url.pathname === `/contacts/${contact.id}`);
      return `contact link -> /contacts/${contact.id}`;
    });

    // UI-CRM-SLDS Phase 3: the agent panel is always rendered on the lead detail page (state is
    // 'empty' until Jev scores the lead, 'ready' once it has — never a silent absence), and its
    // collapse/expand toggle works.
    await runCheck(ctx, 'leads.detail.agent-panel-present-and-collapsible', async () => {
      await openLead(page, full, 'NEW');
      const panel = page.getByTestId('agent-panel');
      await panel.waitFor({ state: 'visible' });
      const toggle = panel.getByRole('button', { name: /Collapse agent panel|Expand agent panel/ });
      const before = (await toggle.getAttribute('aria-expanded')) === 'true';
      await toggle.click();
      await waitUntil(() => toggle.getAttribute('aria-expanded'), (v) => (v === 'true') !== before, {
        describe: (v) => `aria-expanded stayed "${v}" after clicking the collapse toggle`,
      });
      await toggle.click();
      await waitUntil(() => toggle.getAttribute('aria-expanded'), (v) => (v === 'true') === before, {
        describe: (v) => `aria-expanded is "${v}" after toggling twice, expected it restored to ${before}`,
      });
      return `collapse/expand toggles aria-expanded (started ${before})`;
    });

    const bare = await ensure(ctx, 'lead', { source: null, estimatedValue: null });
    await runCheck(ctx, 'leads.detail.empty-cards-hidden', async () => {
      await openLead(page, bare, 'NEW');
      for (const title of ['Contact', 'Organization', 'Description', 'Lost Reason']) {
        if (await scopeLocator(page, { kind: 'card', title }).isVisible()) {
          throw new Error(`"${title}" card is rendered for a lead that has none`);
        }
      }
      const info = (await scopeLocator(page, { kind: 'card', title: 'Lead Information' }).innerText()).replace(/\s+/g, ' ');
      if (!/Source\s*—/i.test(info) || !/Estimated Value\s*—/i.test(info)) {
        throw new Error(`empty Source / Estimated Value should render "—": ${info}`);
      }
    });

    // H-CRM-020: the list shows a humanised badge ("New") while the detail chip prints the enum.
    await runCheck(ctx, 'leads.detail.status-chip-is-humanised', async () => {
      const row = await findRow(page, full.title);
      const badge = (await row.getByRole('cell').nth(1).innerText()).trim();
      await openLead(page, full);
      const chip = (await page.locator('.detail-header mat-chip').first().innerText()).trim();
      if (chip !== badge) throw defect(`list badge says "${badge}" but the detail chip says "${chip}"`);
      return `"${chip}" on both`;
    }, { hypothesis: 'H-CRM-020' });

    await runCheck(ctx, 'leads.detail.back-returns-to-list', async () => {
      await openLead(page, full);
      await page.getByRole('button', { name: 'Go back to leads', exact: true }).click();
      await page.waitForURL((url) => url.pathname === list.path);
    });

    await runCheck(ctx, 'leads.detail.delete-cancel-keeps-record', async () => {
      await openLead(page, bare);
      await withNativeDialog(page, { type: 'confirm', message: `Delete lead "${bare.title}"?`, accept: false },
        () => page.getByRole('button', { name: 'Delete', exact: true }).click());
      if (await isGone(api, bare.id)) throw new Error('record disappeared although the confirm was cancelled');
    });

    // The "Mark Lead as Lost" dialog as a form: label, placeholder, required error, gated submit.
    await runFieldContract(ctx, { ...detail, path: `/leads/${bare.id}` }, detail.forms[0]);

    // ── lifecycle workflow ────────────────────────────────────────────────────
    await runLeadLifecycle(ctx, detail);

    // H-CRM-017: Validators.required accepts "   ", and the component only tests truthiness.
    await runCheck(ctx, 'leads.lost.whitespace-reason-rejected', async () => {
      const lead = await ensure(ctx, 'lead');
      const loseForm = detail.forms[0];
      await openLead(page, lead, 'NEW');
      await page.getByRole('button', { name: 'Mark Lost', exact: true }).click();
      const dialog = scopeLocator(page, loseForm.scope);
      await controlLocator(dialog, loseForm.fields[0]).fill('   ');
      const submit = dialog.getByRole('button', { name: loseForm.submit.name, exact: true });
      const enabled = await waitUntil(() => submit.isEnabled(), Boolean, { timeoutMs: 1500 }).catch(() => false);
      if (!enabled) {
        await dialog.getByRole('button', { name: 'Cancel', exact: true }).click();
        return 'whitespace-only reason keeps the submit button disabled';
      }
      const responded = page.waitForResponse((r) => r.url().includes(`${API}/${lead.id}/lose`), { timeout: 8000 }).catch(() => null);
      await submit.click();
      const response = await responded;
      await page.waitForTimeout(300);
      const dto = await api.get(`${API}/${lead.id}`);
      await dismissSnackbars(page);
      throw defect(`dialog accepts a whitespace-only reason; POST /lose answered ${response?.status() ?? 'nothing'}, lead is now ${dto.status} (lostReason=${JSON.stringify(dto.lostReason)})`);
    }, { hypothesis: 'H-CRM-017' });

    // ── list: search, tabs, filter ────────────────────────────────────────────
    await runCheck(ctx, 'leads.search.finds-by-title', async () => {
      const row = await findRow(page, full.title);
      const text = (await row.innerText()).replace(/\s+/g, ' ');
      for (const value of [full.title, 'New', 'HIGH', 'Webinar', '$1,500.00', contactName, org.name]) {
        if (!text.includes(value)) throw new Error(`row does not show "${value}": ${text}`);
      }
      return text.slice(0, 120);
    });

    // UI-CRM-SLDS Phase 2: the list renders the shared DataTableComponent, not a raw mat-table.
    await runCheck(ctx, 'leads.list.uses-shared-data-table', async () => {
      await findRow(page, full.title);
      await page.getByTestId('data-table').waitFor({ state: 'visible', timeout: 5000 });
    });

    await runCheck(ctx, 'leads.list.empty-cells-render-dash', async () => {
      const row = await findRow(page, bare.title);
      const cells = (await row.getByRole('cell').allInnerTexts()).map((t) => t.trim());
      // Title, Status, Priority, Source, Estimated Value, Contact, Organization, actions
      for (const index of [3, 4, 5, 6]) {
        if (cells[index] !== '—') throw new Error(`cell ${index} renders "${cells[index]}", expected "—"`);
      }
    });

    await runCheck(ctx, 'leads.search.clear-restores-list', async () => {
      await findRow(page, full.title);
      await page.getByRole('button', { name: 'Clear search', exact: true }).click();
      await waitUntil(() => searchBox(page).inputValue(), (value) => value === '', {
        describe: (value) => `search box still holds "${value}" after Clear`,
      });
    });

    await runCheck(ctx, 'leads.row.click-opens-detail', async () => {
      const row = await findRow(page, full.title);
      await row.getByRole('cell').nth(3).click();
      await page.waitForURL((url) => url.pathname === `/leads/${full.id}`);
    });

    await runCheck(ctx, 'leads.tabs.labels', async () => {
      await goto(page, list.path);
      await page.getByRole('tab').first().waitFor({ state: 'visible' });
      const labels = (await page.getByRole('tab').allInnerTexts()).map((t) => t.trim());
      const expected = STATUS_TABS.map((t) => t.label);
      if (JSON.stringify(labels) !== JSON.stringify(expected)) throw new Error(`tabs are [${labels}], expected [${expected}]`);
      return labels.join(' | ');
    });

    // One lead per reachable status so that every tab has something to show.
    const byStatus = {
      NEW: full,
      CONTACTED: await leadIn(ctx, 'CONTACTED'),
      QUALIFIED: await leadIn(ctx, 'QUALIFIED'),
      CONVERTED: await leadIn(ctx, 'CONVERTED'),
      LOST: await leadIn(ctx, 'LOST'),
    };

    for (const tab of STATUS_TABS.filter((t) => t.status)) {
      await runCheck(ctx, `leads.tabs.${tab.label}`, async () => {
        await goto(page, list.path);
        const requested = page.waitForRequest((r) => r.url().includes(`${API}?`) && r.url().includes(`status=${tab.status}`), { timeout: 8000 })
          .catch(() => null);
        await page.getByRole('tab', { name: tab.label, exact: true }).click();
        if (!(await requested)) throw new Error(`selecting the tab did not request status=${tab.status}`);
        await page.waitForLoadState('networkidle').catch(() => {});
        const rows = page.locator('tr.mat-mdc-row');
        const count = await waitUntil(() => rows.count(), (n) => n > 0, { timeoutMs: 4000 }).catch(() => 0);
        if (!count) {
          if (byStatus[tab.status]) throw new Error(`a ${tab.status} lead exists but the tab shows no rows`);
          await page.getByText('No matching leads', { exact: true }).waitFor({ state: 'visible' });
          return 'no leads in this status — "No matching leads" shown';
        }
        const badges = (await rows.locator('td:nth-child(2)').allInnerTexts()).map((t) => t.trim());
        const wrong = badges.filter((b) => b !== tab.label);
        if (wrong.length) throw new Error(`tab "${tab.label}" lists rows with status ${[...new Set(wrong)].join(', ')}`);
        return `${count} row(s), all "${tab.label}"`;
      });
    }

    await runCheck(ctx, 'leads.filter.priority.options', async () => {
      await goto(page, list.path);
      const scope = scopeLocator(page, { kind: 'page' });
      const shown = await readField(scope, priorityFilter);
      if (shown !== priorityFilter.default) throw new Error(`filter shows "${shown}", expected "${priorityFilter.default}"`);
      const actual = await selectOptions(page, scope, priorityFilter);
      const expected = priorityFilter.options.map((o) => o.label);
      if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`options are [${actual}], expected [${expected}]`);
      return actual.join(' | ');
    });

    await runCheck(ctx, 'leads.filter.priority.filters-rows', async () => {
      await goto(page, list.path);
      const scope = scopeLocator(page, { kind: 'page' });
      const requested = page.waitForRequest((r) => r.url().includes(`${API}?`) && r.url().includes('priority=HIGH'), { timeout: 8000 })
        .catch(() => null);
      await fillField(page, scope, priorityFilter, 'HIGH');
      if (!(await requested)) throw new Error('choosing HIGH did not request priority=HIGH');
      await page.waitForLoadState('networkidle').catch(() => {});
      const rows = page.locator('tr.mat-mdc-row');
      await waitUntil(() => rows.count(), (n) => n > 0, { timeoutMs: 5000, describe: () => 'no rows although a HIGH lead exists' });
      const values = (await rows.locator('td:nth-child(3)').allInnerTexts()).map((t) => t.trim());
      const wrong = values.filter((v) => v !== 'HIGH');
      if (wrong.length) throw new Error(`HIGH filter lists priorities ${[...new Set(wrong)].join(', ')}`);
      return `${values.length} row(s), all HIGH`;
    });

    await runCheck(ctx, 'leads.list.page-sizes', async () => {
      await goto(page, list.path);
      await page.locator('mat-paginator mat-form-field').click();
      const options = (await page.getByRole('option').allInnerTexts()).map((t) => t.trim());
      await page.keyboard.press('Escape');
      await settleOverlays(page);
      const expected = list.tables[0].pageSizes.map(String);
      if (JSON.stringify(options) !== JSON.stringify(expected)) throw new Error(`page sizes are [${options}], expected [${expected}]`);
    });

    // ── list: row menus per status ────────────────────────────────────────────
    const menuFor = (status) => list.actions.filter((a) => a.in === 'menu' && a.visibleWhen.includes(status)).map((a) => a.name);

    for (const status of ['NEW', 'CONTACTED', 'QUALIFIED', 'LOST']) {
      await runCheck(ctx, `leads.row-menu.${status}`, async () => {
        const items = await openRowMenu(page, byStatus[status].title);
        const path = new URL(page.url()).pathname;
        if (path !== list.path) throw new Error(`opening the row menu navigated to ${path}`);
        await page.keyboard.press('Escape');
        await settleOverlays(page);
        const expected = menuFor(status);
        if (JSON.stringify(items) !== JSON.stringify(expected)) throw new Error(`menu is [${items}], expected [${expected}]`);
        return items.join(' | ');
      });
    }

    // H-CRM-015: every item is conditional and none applies to CONVERTED, yet the trigger stays
    // enabled — the user opens an empty panel.
    await runCheck(ctx, 'leads.row-menu.CONVERTED-is-not-empty', async () => {
      const items = await openRowMenu(page, byStatus.CONVERTED.title);
      await page.keyboard.press('Escape');
      await settleOverlays(page);
      if (!items.length) throw defect('the row menu of a CONVERTED lead opens with zero items (trigger is enabled)');
      return items.join(' | ');
    }, { hypothesis: 'H-CRM-015' });

    await runCheck(ctx, 'leads.list.menu.mark-contacted', async () => {
      const lead = await ensure(ctx, 'lead');
      await openRowMenu(page, lead.title);
      await page.getByRole('menuitem', { name: 'Mark Contacted' }).click();
      await expectApiStatus(api, lead.id, 'CONTACTED');
      await expectNoSnackbar(page);
    });

    await runCheck(ctx, 'leads.list.menu.convert-uses-mat-dialog', async () => {
      const lead = await leadIn(ctx, 'QUALIFIED');
      await openRowMenu(page, lead.title);
      await expectNoNativeDialog(page, () => page.getByRole('menuitem', { name: 'Convert to Deal' }).click());
      await confirmMatDialog(page, { title: 'Convert lead', message: `Convert "${lead.title}" to a deal?`, click: 'Convert' });
      await page.waitForURL(/\/deals\/(\d+)$/, { timeout: 15000 });
      const dealId = page.url().match(/\/deals\/(\d+)$/)[1];
      api.track('deal', dealId, `/api/v1/deals/${dealId}`);
      await expectApiStatus(api, lead.id, 'CONVERTED');
      return `-> /deals/${dealId}`;
    });

    await runCheck(ctx, 'leads.list.menu.mark-lost-dialog', async () => {
      const lead = await ensure(ctx, 'lead');
      await openRowMenu(page, lead.title);
      await page.getByRole('menuitem', { name: 'Mark Lost' }).click();
      const dialog = scopeLocator(page, detail.forms[0].scope);
      await controlLocator(dialog, detail.forms[0].fields[0]).fill('Lost from the list (UAT)');
      await dialog.getByRole('button', { name: 'Mark as Lost', exact: true }).click();
      const dto = await expectApiStatus(api, lead.id, 'LOST');
      if (dto.lostReason !== 'Lost from the list (UAT)') throw new Error(`lostReason is "${dto.lostReason}"`);
    });

    await runCheck(ctx, 'leads.list.menu.delete-uses-mat-dialog', async () => {
      const lead = await ensure(ctx, 'lead');
      await openRowMenu(page, lead.title);
      await expectNoNativeDialog(page, () => page.getByRole('menuitem', { name: 'Delete' }).click());
      await confirmMatDialog(page, { title: 'Delete lead', message: `Delete lead "${lead.title}"?`, click: 'Cancel' });
      if (await isGone(api, lead.id)) throw new Error('record was deleted although the dialog was cancelled');
      await openRowMenu(page, lead.title);
      await page.getByRole('menuitem', { name: 'Delete' }).click();
      await confirmMatDialog(page, { title: 'Delete lead', click: 'Delete' });
      await waitUntil(() => isGone(api, lead.id), Boolean, { timeoutMs: 8000, describe: () => 'record still readable after confirming delete' });
      api.untrack('lead', lead.id);
      await expectNoSnackbar(page);
    });

    // ── form behaviour ────────────────────────────────────────────────────────
    await runCheck(ctx, 'leads.form.cancel-returns-to-list', async () => {
      await goto(page, '/leads/new');
      await fillField(page, formScope(), field('Title'), 'Discarded');
      await formScope().getByRole('button', { name: 'Cancel', exact: true }).click();
      await page.waitForURL((url) => url.pathname === list.path);
    });

    const clearButton = (label) => formFieldLocator(formScope(), field(label)).getByRole('button', { name: 'Clear', exact: true });

    await runCheck(ctx, 'leads.form.autocomplete-pick-and-clear', async () => {
      const title = uniqueName(runId, 'lead');
      await goto(page, '/leads/new');
      await fillField(page, formScope(), field('Title'), title);
      for (const label of ['Contact', 'Organization']) {
        if (await clearButton(label).isVisible()) throw new Error(`"Clear" on ${label} is visible before anything is picked`);
      }
      await fillField(page, formScope(), field('Contact'), contact.lastName);
      await fillField(page, formScope(), field('Organization'), org.name);
      const shown = await readField(formScope(), field('Contact'));
      if (shown !== contactName) throw new Error(`picked contact displays as "${shown}", expected "${contactName}"`);
      await clearButton('Organization').waitFor({ state: 'visible' });
      await clearButton('Contact').click();
      await waitUntil(() => readField(formScope(), field('Contact')), (v) => v === '', { describe: (v) => `Contact still holds "${v}" after Clear` });
      await formScope().getByRole('button', { name: 'Create', exact: true }).click();
      await page.waitForURL(DETAIL_URL, { timeout: 15000 });
      const id = page.url().match(DETAIL_URL)[1];
      api.track('lead', id, `${API}/${id}`);
      await expectNoSnackbar(page);
      const dto = await api.get(`${API}/${id}`);
      if (dto.organizationId !== org.id) throw new Error(`organizationId is ${dto.organizationId}, picked ${org.id}`);
      if (dto.contactId != null) throw new Error(`contactId is ${dto.contactId} although the contact was cleared`);
      return `organizationId=${dto.organizationId}, contactId=null after Clear`;
    });

    await runCheck(ctx, 'leads.form.contact-pick-persists', async () => {
      const title = uniqueName(runId, 'lead');
      await goto(page, '/leads/new');
      await fillField(page, formScope(), field('Title'), title);
      await fillField(page, formScope(), field('Contact'), contact.lastName);
      await formScope().getByRole('button', { name: 'Create', exact: true }).click();
      await page.waitForURL(DETAIL_URL, { timeout: 15000 });
      const id = page.url().match(DETAIL_URL)[1];
      api.track('lead', id, `${API}/${id}`);
      const dto = await api.get(`${API}/${id}`);
      if (dto.contactId !== contact.id) throw new Error(`contactId is ${dto.contactId}, picked ${contact.id}`);
      if (dto.priority !== 'MEDIUM') throw new Error(`untouched Priority saved as ${dto.priority}, expected the MEDIUM default`);
      return `contactId=${dto.contactId}, priority=${dto.priority}`;
    });

    // H-CRM-023: typed-but-not-picked autocomplete text is dropped without a warning.
    await runCheck(ctx, 'leads.form.organization-typed-not-picked', async () => {
      const title = uniqueName(runId, 'lead');
      await goto(page, '/leads/new');
      await fillField(page, formScope(), field('Title'), title);
      const responded = page.waitForResponse((r) => /organizations\/search/.test(r.url()), { timeout: 8000 }).catch(() => null);
      await controlLocator(formScope(), field('Organization')).fill(org.name);
      await responded;
      await page.keyboard.press('Escape');
      await settleOverlays(page);
      await formScope().getByRole('button', { name: 'Create', exact: true }).click();
      await page.waitForURL(DETAIL_URL, { timeout: 15000 });
      const id = page.url().match(DETAIL_URL)[1];
      api.track('lead', id, `${API}/${id}`);
      const dto = await api.get(`${API}/${id}`);
      if (dto.organizationId == null) {
        throw defect(`typed "${org.name}" (an exact match) without picking it: saved with organizationId=null and no warning`);
      }
      return `organizationId=${dto.organizationId}`;
    }, { hypothesis: 'H-CRM-023' });

    // H-CRM-034: edit mode patches *names* into controls whose displayWith expects DTOs.
    await runCheck(ctx, 'leads.form.edit-shows-linked-records', async () => {
      await goto(page, `/leads/${full.id}/edit`);
      await waitUntil(() => readField(formScope(), field('Title')), (v) => v === full.title, { describe: (v) => `Title holds "${v}"` });
      await page.waitForTimeout(500);
      const shownContact = await readField(formScope(), field('Contact'));
      const shownOrg = await readField(formScope(), field('Organization'));
      if (shownContact !== contactName || shownOrg !== org.name) {
        throw defect(`lead is linked to "${contactName}" / "${org.name}" but the edit form shows Contact="${shownContact}", Organization="${shownOrg}"`);
      }
    }, { hypothesis: 'H-CRM-034' });

    // H-CRM-016: no Validators.min(0) on Estimated Value although the API is @PositiveOrZero.
    await runCheck(ctx, 'leads.form.negative-estimated-value-rejected', async () => {
      const title = uniqueName(runId, 'lead');
      await goto(page, '/leads/new');
      await fillField(page, formScope(), field('Title'), title);
      await fillField(page, formScope(), field('Estimated Value'), '-5');
      await page.keyboard.press('Tab');
      const submit = formScope().getByRole('button', { name: 'Create', exact: true });
      const error = formFieldLocator(formScope(), field('Estimated Value')).locator('mat-error').first();
      const blockedByForm = await waitUntil(async () => !(await submit.isEnabled()) || (await error.isVisible()), Boolean, { timeoutMs: 1500 })
        .catch(() => false);
      if (blockedByForm) return 'the form rejects a negative value';
      const responded = page.waitForResponse((r) => r.url().endsWith(API) && r.request().method() === 'POST', { timeout: 8000 }).catch(() => null);
      await submit.click();
      const response = await responded;
      const status = response?.status();
      let saved = '';
      if (status && status < 300) {
        await page.waitForURL(DETAIL_URL, { timeout: 15000 });
        const id = page.url().match(DETAIL_URL)[1];
        api.track('lead', id, `${API}/${id}`);
        saved = ` and the lead was SAVED with estimatedValue=${(await api.get(`${API}/${id}`)).estimatedValue}`;
      }
      await dismissSnackbars(page);
      throw defect(`form accepts -5 with no mat-error and an enabled Create; POST ${API} answered ${status ?? 'nothing'}${saved}`);
    }, { hypothesis: 'H-CRM-016' });
  },
};
