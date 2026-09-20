/**
 * Deals suite: shell + field contract + CRUD from the spec, the detail lifecycle and kanban
 * workflows, and hand-written checks for stage tabs, per-stage row menus, the list's native
 * confirmations, date handling (including non-UTC browsers) and the registered hypotheses.
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, defect, waitUntil } from '../../lib/health.mjs';
import { withNativeDialog } from '../../lib/dialogs.mjs';
import { settleOverlays, expectNoSnackbar, dismissSnackbars } from '../../lib/overlays.mjs';
import { scopeLocator, controlLocator, fillField, readField } from '../../lib/fields.mjs';
import { ApiCallError } from '../../lib/api.mjs';
import { ensure, uniqueName } from '../fixtures.mjs';
import { list, detail, kanban, STAGES } from '../specs/deals.mjs';
import { runShell } from '../runners/shell.mjs';
import { runFieldContract } from '../runners/fieldContract.mjs';
import { runCrud } from '../runners/crud.mjs';
import { runDealLifecycle, openDeal, expectApiStage } from '../workflows/deal-lifecycle.mjs';
import { runKanban } from '../workflows/kanban-menus.mjs';

const API = '/api/v1/deals';
const DETAIL_URL = /\/deals\/(\d+)$/;
const TRIGGER = 'Deal actions';

/** Run-unique names end in a counter, so "…-deal-3" must not also match "…-deal-30". */
const named = (name) => new RegExp(`${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?!\\d)`);

const searchBox = (page) => page.getByRole('textbox', { name: list.searchPlaceholder, exact: true });

/** Type a query into the list search box and wait for the search response. */
async function search(page, query) {
  await goto(page, list.path);
  const responded = page.waitForResponse((r) => r.url().includes(`${API}/search`) && r.ok(), { timeout: 10000 })
    .catch(() => null);
  await searchBox(page).fill(query);
  await responded;
}

/** Search by the run-unique name and return the table row. */
async function findRow(page, name) {
  await search(page, name);
  const row = page.getByRole('row').filter({ hasText: named(name) }).first();
  await row.waitFor({ state: 'visible' });
  return row;
}

/** Open the row menu of a deal (scoped by row: the trigger label repeats) and return item labels. */
async function openRowMenu(page, name) {
  const row = await findRow(page, name);
  await row.getByRole('button', { name: TRIGGER, exact: true }).click();
  await page.getByRole('menuitem').first().waitFor({ state: 'visible' });
  return (await page.getByRole('menuitem').allInnerTexts()).map((t) => t.replace(/^[a-z_]+\s+/, '').replace(/\s+/g, ' ').trim());
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

/** Drive a deal to a stage through the API (create ignores `stage`). */
async function dealIn(ctx, stage, overrides = {}) {
  const deal = await ensure(ctx, 'deal', overrides);
  if (stage === 'WON') return ctx.api.post(`${API}/${deal.id}/win`);
  if (stage === 'LOST') return ctx.api.post(`${API}/${deal.id}/lose`, { reason: 'UAT fixture' });
  let current = deal;
  for (let i = 0; i < STAGES.findIndex((s) => s.value === stage); i++) current = await ctx.api.post(`${API}/${deal.id}/advance`);
  return current;
}

export default {
  id: 'deals',
  backend: 'crm',

  async run(ctx) {
    const { page, api, runId } = ctx;
    const baseForm = list.forms[0];
    const field = (label) => baseForm.fields.find((f) => f.label === label);
    const formScope = (p = page) => scopeLocator(p, baseForm.scope);

    const org = await ensure(ctx, 'organization');
    const contact = await ensure(ctx, 'contact');
    const contactName = `${contact.firstName} ${contact.lastName}`;
    const form = {
      ...baseForm,
      fields: baseForm.fields.map((f) => (f.label === 'Organization' ? { ...f, sample: org.name } : f)),
    };

    /** Fill the create form, submit, ledger the new deal and return its id. */
    const createThroughUi = async (p, values) => {
      await goto(p, '/deals/new');
      for (const [label, value] of Object.entries(values)) await fillField(p, formScope(p), field(label), value);
      await formScope(p).getByRole('button', { name: 'Create', exact: true }).click();
      await p.waitForURL(DETAIL_URL, { timeout: 15000 });
      const id = p.url().match(DETAIL_URL)[1];
      api.track('deal', id, `${API}/${id}`);
      return id;
    };

    await runShell(ctx, list);
    await runFieldContract(ctx, list, baseForm);

    // No deleteAction: the detail page has no Delete (H-CRM-025); the record is torn down by the ledger.
    await runCrud(ctx, list, form, {
      entity: 'deal',
      apiPath: API,
      nameField: 'Deal Name',
      detailUrl: DETAIL_URL,
      editPath: (id) => `/deals/${id}/edit`,
    });

    // ── detail ────────────────────────────────────────────────────────────────
    const full = await ensure(ctx, 'deal', {
      amount: 8800, primaryContactId: contact.id, organizationId: org.id,
      description: 'Detail fixture description', expectedCloseDate: '2027-06-30',
    });
    await runShell(ctx, detail, { path: `/deals/${full.id}`, heading: full.name });

    await runCheck(ctx, 'deals.detail.populated-cards', async () => {
      await openDeal(page, full, 'PROSPECTING');
      const info = (await scopeLocator(page, { kind: 'card', title: 'Deal Information' }).innerText()).replace(/\s+/g, ' ');
      for (const value of ['$8,800', '10%', 'Jun 30, 2027']) {
        if (!info.includes(value)) throw new Error(`Deal Information card does not show "${value}": ${info}`);
      }
      await scopeLocator(page, { kind: 'card', title: 'Description' }).getByText('Detail fixture description').waitFor({ state: 'visible' });
      const relations = scopeLocator(page, { kind: 'card', title: 'Relationships' });
      await relations.getByRole('link', { name: contactName }).waitFor({ state: 'visible' });
      await relations.getByRole('link', { name: org.name }).click();
      await page.waitForURL((url) => url.pathname === `/organizations/${org.id}`);
      return `organization link -> /organizations/${org.id}`;
    });

    const bare = await ensure(ctx, 'deal', { amount: null });
    await runCheck(ctx, 'deals.detail.empty-state-texts', async () => {
      await openDeal(page, bare, 'PROSPECTING');
      const emptyText = detail.cards.find((c) => c.title === 'Relationships').emptyText;
      await scopeLocator(page, { kind: 'card', title: 'Relationships' }).getByText(emptyText, { exact: true }).waitFor({ state: 'visible' });
      for (const title of ['Lost Reason', 'Description']) {
        if (await scopeLocator(page, { kind: 'card', title }).isVisible()) throw new Error(`"${title}" card is rendered for a deal that has none`);
      }
      const info = (await scopeLocator(page, { kind: 'card', title: 'Deal Information' }).innerText()).replace(/\s+/g, ' ');
      if (!/Amount\s*—/i.test(info) || !/Expected Close\s*—/i.test(info)) throw new Error(`empty Amount / Expected Close should render "—": ${info}`);
    });

    await runCheck(ctx, 'deals.detail.back-returns-to-list', async () => {
      await openDeal(page, full);
      await page.getByRole('button', { name: 'Go back to deals', exact: true }).click();
      await page.waitForURL((url) => url.pathname === list.path);
    });

    // H-CRM-025 is a gap, not a check that can fail: this only notices when a Delete appears.
    await runCheck(ctx, 'deals.detail.has-no-delete-button', async () => {
      await openDeal(page, full);
      if (await page.getByRole('button', { name: 'Delete', exact: true }).isVisible()) {
        throw new Error('the detail page now has a Delete button — retire gap H-CRM-025 and pass deleteAction to runCrud');
      }
      return 'still no Delete on the detail page (gap H-CRM-025)';
    });

    // The "Mark Deal as Lost" dialog as a form: label, placeholder, required error, gated submit.
    await runFieldContract(ctx, { ...detail, path: `/deals/${bare.id}` }, detail.forms[0]);

    // ── workflows ─────────────────────────────────────────────────────────────
    await runDealLifecycle(ctx, detail);
    await runKanban(ctx, { kanban, detail });

    // H-CRM-018: Validators.required accepts "   ", and the component only tests truthiness.
    await runCheck(ctx, 'deals.lost.whitespace-reason-rejected', async () => {
      const deal = await ensure(ctx, 'deal');
      const loseForm = detail.forms[0];
      await openDeal(page, deal, 'PROSPECTING');
      await page.getByRole('button', { name: 'Lost', exact: true }).click();
      const dialog = scopeLocator(page, loseForm.scope);
      await controlLocator(dialog, loseForm.fields[0]).fill('   ');
      const submit = dialog.getByRole('button', { name: loseForm.submit.name, exact: true });
      const enabled = await waitUntil(() => submit.isEnabled(), Boolean, { timeoutMs: 1500 }).catch(() => false);
      if (!enabled) {
        await dialog.getByRole('button', { name: 'Cancel', exact: true }).click();
        return 'whitespace-only reason keeps the submit button disabled';
      }
      const responded = page.waitForResponse((r) => r.url().includes(`${API}/${deal.id}/lose`), { timeout: 8000 }).catch(() => null);
      await submit.click();
      const response = await responded;
      await page.waitForTimeout(300);
      const dto = await api.get(`${API}/${deal.id}`);
      await dismissSnackbars(page);
      throw defect(`dialog accepts a whitespace-only reason; POST /lose answered ${response?.status() ?? 'nothing'}, deal is now ${dto.stage} (lostReason=${JSON.stringify(dto.lostReason)})`);
    }, { hypothesis: 'H-CRM-018' });

    // H-CRM-012: `deal.probability ? … : '—'` treats a legitimate 0 as "no value".
    await runCheck(ctx, 'deals.probability-zero-renders-as-percent', async () => {
      const lost = await dealIn(ctx, 'LOST');
      const dto = await api.get(`${API}/${lost.id}`);
      if (dto.probability !== 0) throw new Error(`fixture problem: a LOST deal has probability ${dto.probability}, expected 0`);
      const row = await findRow(page, lost.name);
      const inList = (await row.getByRole('cell').nth(3).innerText()).trim();
      await openDeal(page, lost, 'LOST');
      const info = (await scopeLocator(page, { kind: 'card', title: 'Deal Information' }).innerText()).replace(/\s+/g, ' ');
      const inDetail = info.match(/Probability\s*(\S+)/i)?.[1];
      if (inList !== '0%' || inDetail !== '0%') {
        throw defect(`API probability is 0 but the list shows "${inList}" and the detail shows "${inDetail}"`);
      }
    }, { hypothesis: 'H-CRM-012' });

    // H-CRM-013: the list pipes a null amount straight into `currency`, which prints nothing.
    await runCheck(ctx, 'deals.list.null-amount-renders-dash', async () => {
      const row = await findRow(page, bare.name);
      const amount = (await row.getByRole('cell').nth(1).innerText()).trim();
      if (amount !== '—') throw defect(`a deal without an amount renders "${amount}" in the Amount column (other empty cells render "—")`);
    }, { hypothesis: 'H-CRM-013' });

    // ── list ──────────────────────────────────────────────────────────────────
    await runCheck(ctx, 'deals.search.finds-by-name', async () => {
      const row = await findRow(page, full.name);
      const text = (await row.innerText()).replace(/\s+/g, ' ');
      for (const value of [full.name, '$8,800', 'Prospecting', '10%', 'Jun 30, 2027', contactName]) {
        if (!text.includes(value)) throw new Error(`row does not show "${value}": ${text}`);
      }
      return text.slice(0, 120);
    });

    await runCheck(ctx, 'deals.search.clear-restores-list', async () => {
      await findRow(page, full.name);
      await page.getByRole('button', { name: 'Clear search', exact: true }).click();
      await waitUntil(() => searchBox(page).inputValue(), (value) => value === '', {
        describe: (value) => `search box still holds "${value}" after Clear`,
      });
    });

    await runCheck(ctx, 'deals.row.click-opens-detail', async () => {
      const row = await findRow(page, full.name);
      await row.getByRole('cell').nth(2).click();
      await page.waitForURL((url) => url.pathname === `/deals/${full.id}`);
    });

    await runCheck(ctx, 'deals.list.page-sizes', async () => {
      await goto(page, list.path);
      await page.locator('mat-paginator mat-form-field').click();
      const options = (await page.getByRole('option').allInnerTexts()).map((t) => t.trim());
      await page.keyboard.press('Escape');
      await settleOverlays(page);
      const expected = list.tables[0].pageSizes.map(String);
      if (JSON.stringify(options) !== JSON.stringify(expected)) throw new Error(`page sizes are [${options}], expected [${expected}]`);
    });

    await runCheck(ctx, 'deals.tabs.labels', async () => {
      await goto(page, list.path);
      await page.getByRole('tab').first().waitFor({ state: 'visible' });
      const labels = (await page.getByRole('tab').allInnerTexts()).map((t) => t.trim());
      const expected = ['All', ...STAGES.map((s) => s.label)];
      if (JSON.stringify(labels) !== JSON.stringify(expected)) throw new Error(`tabs are [${labels}], expected [${expected}]`);
      return labels.join(' | ');
    });

    // One deal per stage so that every tab has something to show.
    const byStage = { PROSPECTING: full };
    for (const stage of STAGES.slice(1)) byStage[stage.value] = await dealIn(ctx, stage.value);
    const won = byStage.WON;

    for (const stage of STAGES) {
      await runCheck(ctx, `deals.tabs.${stage.label}`, async () => {
        await goto(page, list.path);
        const requested = page.waitForRequest((r) => r.url().includes(`${API}?`) && r.url().includes(`stage=${stage.value}`), { timeout: 8000 })
          .catch(() => null);
        await page.getByRole('tab', { name: stage.label, exact: true }).click();
        if (!(await requested)) throw new Error(`selecting the tab did not request stage=${stage.value}`);
        await page.waitForLoadState('networkidle').catch(() => {});
        const rows = page.locator('tr.mat-mdc-row');
        await waitUntil(() => rows.count(), (n) => n > 0, { timeoutMs: 5000, describe: () => `a ${stage.value} deal exists but the tab shows no rows` });
        const badges = (await rows.locator('td:nth-child(3)').allInnerTexts()).map((t) => t.trim());
        const wrong = badges.filter((b) => b !== stage.label);
        if (wrong.length) throw new Error(`tab "${stage.label}" lists rows in stage ${[...new Set(wrong)].join(', ')}`);
        return `${badges.length} row(s), all "${stage.label}"`;
      });
    }

    const menuFor = (stage) => list.actions.filter((a) => a.in === 'menu' && a.visibleWhen.includes(stage)).map((a) => a.name);
    for (const stage of ['PROSPECTING', 'NEGOTIATION', 'WON', 'LOST']) {
      await runCheck(ctx, `deals.row-menu.${stage}`, async () => {
        const items = await openRowMenu(page, byStage[stage].name);
        const path = new URL(page.url()).pathname;
        await page.keyboard.press('Escape');
        await settleOverlays(page);
        if (path !== list.path) throw new Error(`opening the row menu navigated to ${path}`);
        const expected = menuFor(stage);
        if (JSON.stringify(items) !== JSON.stringify(expected)) throw new Error(`menu is [${items}], expected [${expected}]`);
        return items.join(' | ');
      });
    }

    // H-A11Y-003 (weak): every row's trigger is announced as the same "Deal actions".
    await runCheck(ctx, 'deals.a11y.row-menu-names-are-distinct', async () => {
      await search(page, `UAT-${runId}-deal`);
      const triggers = page.getByRole('row').getByRole('button', { name: /actions/i });
      await waitUntil(() => triggers.count(), (n) => n >= 2, { timeoutMs: 6000, describe: (n) => `only ${n} row(s) to compare` });
      const labels = await triggers.evaluateAll((nodes) => nodes.map((n) => n.getAttribute('aria-label') ?? ''));
      const distinct = new Set(labels);
      if (distinct.size < labels.length) {
        throw defect(`${labels.length} row menu triggers share ${distinct.size} accessible name(s): ${[...distinct].map((l) => `"${l}"`).join(', ')} (leads use "Actions for <title>")`);
      }
    }, { hypothesis: 'H-A11Y-003' });

    await runCheck(ctx, 'deals.list.menu.advance', async () => {
      const deal = await ensure(ctx, 'deal');
      await openRowMenu(page, deal.name);
      await page.getByRole('menuitem', { name: 'Advance Stage' }).click();
      await expectApiStage(api, deal.id, 'QUALIFICATION');
      await expectNoSnackbar(page);
    });

    // H-CRM-037: row actions call refreshList(), which reloads the unfiltered list and ignores the
    // active search — the box still says "UAT-…" while the table shows every deal.
    await runCheck(ctx, 'deals.list.menu-action-keeps-search-results', async () => {
      const deal = await ensure(ctx, 'deal');
      await openRowMenu(page, deal.name);
      const reloaded = page.waitForResponse((r) => r.url().includes(`${API}?`) && r.ok(), { timeout: 8000 }).catch(() => null);
      await page.getByRole('menuitem', { name: 'Advance Stage' }).click();
      await expectApiStage(api, deal.id, 'QUALIFICATION');
      await reloaded;
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.locator('tr.mat-mdc-row').first().waitFor({ state: 'visible' });
      const query = await searchBox(page).inputValue();
      const names = (await page.locator('tr.mat-mdc-row td:first-child').allInnerTexts()).map((t) => t.trim());
      const strangers = names.filter((n) => !n.includes(query));
      if (query && strangers.length) {
        throw defect(`search box still holds "${query}" but after the row action the table lists ${names.length} rows, ${strangers.length} of them not matching (e.g. "${strangers[0]}")`);
      }
      return `${names.length} row(s), all matching "${query}"`;
    }, { hypothesis: 'H-CRM-037' });

    await runCheck(ctx, 'deals.list.menu.mark-won-native-confirm', async () => {
      const deal = await ensure(ctx, 'deal');
      await openRowMenu(page, deal.name);
      await withNativeDialog(page, { type: 'confirm', message: `Mark "${deal.name}" as Won?`, accept: false },
        () => page.getByRole('menuitem', { name: 'Mark Won' }).click());
      await page.waitForTimeout(400);
      if ((await api.get(`${API}/${deal.id}`)).stage !== 'PROSPECTING') throw new Error('stage changed although the confirm was cancelled');
      await openRowMenu(page, deal.name);
      await withNativeDialog(page, { type: 'confirm', message: `Mark "${deal.name}" as Won?` },
        () => page.getByRole('menuitem', { name: 'Mark Won' }).click());
      await expectApiStage(api, deal.id, 'WON');
    });

    await runCheck(ctx, 'deals.list.menu.mark-lost-dialog', async () => {
      const deal = await ensure(ctx, 'deal');
      await openRowMenu(page, deal.name);
      await page.getByRole('menuitem', { name: 'Mark Lost' }).click();
      const dialog = scopeLocator(page, detail.forms[0].scope);
      await controlLocator(dialog, detail.forms[0].fields[0]).fill('Lost from the list (UAT)');
      await dialog.getByRole('button', { name: 'Mark as Lost', exact: true }).click();
      const dto = await expectApiStage(api, deal.id, 'LOST');
      if (dto.lostReason !== 'Lost from the list (UAT)') throw new Error(`lostReason is "${dto.lostReason}"`);
    });

    await runCheck(ctx, 'deals.list.menu.delete-native-confirm', async () => {
      const deal = await ensure(ctx, 'deal');
      await openRowMenu(page, deal.name);
      await withNativeDialog(page, { type: 'confirm', message: `Delete "${deal.name}"?`, accept: false },
        () => page.getByRole('menuitem', { name: 'Delete' }).click());
      await page.waitForTimeout(400);
      if (await isGone(api, deal.id)) throw new Error('record was deleted although the confirm was cancelled');
      await openRowMenu(page, deal.name);
      await withNativeDialog(page, { type: 'confirm', message: `Delete "${deal.name}"?` },
        () => page.getByRole('menuitem', { name: 'Delete' }).click());
      await waitUntil(() => isGone(api, deal.id), Boolean, { timeoutMs: 8000, describe: () => 'record still readable after confirming delete' });
      api.untrack('deal', deal.id);
      await expectNoSnackbar(page);
    });

    // ── form behaviour ────────────────────────────────────────────────────────
    await runCheck(ctx, 'deals.form.cancel-returns-to-list', async () => {
      await goto(page, '/deals/new');
      await fillField(page, formScope(), field('Deal Name'), 'Discarded');
      await formScope().getByRole('button', { name: 'Cancel', exact: true }).click();
      await page.waitForURL((url) => url.pathname === list.path);
    });

    // H-CRM-010: onSubmit() destructures `stage` out of the create payload.
    await runCheck(ctx, 'deals.form.stage-honoured-on-create', async () => {
      const id = await createThroughUi(page, { 'Deal Name': uniqueName(runId, 'deal'), Stage: 'Negotiation' });
      const dto = await api.get(`${API}/${id}`);
      if (dto.stage !== 'NEGOTIATION') throw defect(`picked Stage "Negotiation" on the create form, the deal was created as ${dto.stage} with no warning`);
    }, { hypothesis: 'H-CRM-010' });

    // H-CRM-031: neither DealCreateRequest nor DealUpdateRequest has a probability; the API derives it.
    await runCheck(ctx, 'deals.form.probability-is-persisted', async () => {
      const id = await createThroughUi(page, { 'Deal Name': uniqueName(runId, 'deal'), 'Probability (%)': '55' });
      const created = (await api.get(`${API}/${id}`)).probability;
      await goto(page, `/deals/${id}/edit`);
      await waitUntil(() => readField(formScope(), field('Deal Name')), (v) => v !== '', { describe: () => 'edit form never loaded' });
      await fillField(page, formScope(), field('Probability (%)'), '65');
      await formScope().getByRole('button', { name: 'Update', exact: true }).click();
      await page.waitForURL(DETAIL_URL, { timeout: 15000 });
      const updated = (await api.get(`${API}/${id}`)).probability;
      if (created !== 55 || updated !== 65) {
        throw defect(`typed 55% on create and 65% on edit; the API holds ${created}% then ${updated}% — the field is editable but never persisted`);
      }
    }, { hypothesis: 'H-CRM-031' });

    // H-CRM-039: PUT maps `stage` onto the entity without recomputing the stage-derived probability.
    await runCheck(ctx, 'deals.form.stage-edit-updates-probability', async () => {
      const deal = await ensure(ctx, 'deal');
      await goto(page, `/deals/${deal.id}/edit`);
      await waitUntil(() => readField(formScope(), field('Deal Name')), (v) => v === deal.name, { describe: (v) => `Deal Name holds "${v}"` });
      await fillField(page, formScope(), field('Stage'), 'Negotiation');
      await formScope().getByRole('button', { name: 'Update', exact: true }).click();
      await page.waitForURL(DETAIL_URL, { timeout: 15000 });
      const viaForm = await expectApiStage(api, deal.id, 'NEGOTIATION');
      const viaAdvance = byStage.NEGOTIATION.probability;
      if (viaForm.probability !== viaAdvance) {
        throw defect(`NEGOTIATION reached through the edit form has probability ${viaForm.probability}% (weighted ${viaForm.weightedAmount}); reached through Advance Stage it is ${viaAdvance}%`);
      }
    }, { hypothesis: 'H-CRM-039' });

    // H-CRM-040: the edit form always sends `stage`, and the API rejects any update that carries a
    // stage once the deal is WON/LOST — yet Edit is offered on closed deals.
    await runCheck(ctx, 'deals.form.closed-deal-can-be-edited', async () => {
      await goto(page, `/deals/${won.id}/edit`);
      await waitUntil(() => readField(formScope(), field('Deal Name')), (v) => v === won.name, { describe: (v) => `Deal Name holds "${v}"` });
      await fillField(page, formScope(), field('Description'), 'Post-mortem note (UAT)');
      const responded = page.waitForResponse((r) => r.url().endsWith(`${API}/${won.id}`) && r.request().method() === 'PUT', { timeout: 8000 })
        .catch(() => null);
      await formScope().getByRole('button', { name: 'Update', exact: true }).click();
      const response = await responded;
      if (response && !response.ok()) {
        const body = await response.text().catch(() => '');
        await dismissSnackbars(page);
        throw defect(`Edit is offered on a WON deal, but changing only the description answers HTTP ${response.status()}: ${body.match(/"message":"([^"]+)"/)?.[1] ?? body.slice(0, 120)}`);
      }
      await page.waitForURL(DETAIL_URL, { timeout: 15000 });
    }, { hypothesis: 'H-CRM-040' });

    await runCheck(ctx, 'deals.form.close-date-persists', async () => {
      const id = await createThroughUi(page, { 'Deal Name': uniqueName(runId, 'deal'), 'Expected Close Date': '3/15/2027' });
      await expectNoSnackbar(page);
      const dto = await api.get(`${API}/${id}`);
      if (dto.expectedCloseDate !== '2027-03-15') throw new Error(`typed 3/15/2027, API holds ${dto.expectedCloseDate}`);
      const info = (await scopeLocator(page, { kind: 'card', title: 'Deal Information' }).innerText()).replace(/\s+/g, ' ');
      if (!info.includes('Mar 15, 2027')) throw new Error(`detail does not show Mar 15, 2027: ${info}`);
      await goto(page, `/deals/${id}/edit`);
      await waitUntil(() => readField(formScope(), field('Expected Close Date')), (v) => v === '3/15/2027', {
        describe: (v) => `edit form shows "${v}", expected 3/15/2027`,
      });
      return 'UTC browser: 3/15/2027 -> 2027-03-15 -> Mar 15, 2027 -> 3/15/2027';
    });

    // H-CRM-021: `toISOString().split('T')[0]` converts local midnight to UTC, which is the previous
    // day anywhere ahead of UTC. The main run is pinned to UTC, so this needs its own browser.
    await runCheck(ctx, 'deals.form.close-date-survives-timezone-ahead-of-utc', async () => {
      const session = await ctx.launch({ contextOptions: { locale: 'en-US', timezoneId: 'Pacific/Auckland' } });
      try {
        const id = await createThroughUi(session.page, { 'Deal Name': uniqueName(runId, 'deal'), 'Expected Close Date': '3/15/2027' });
        const dto = await api.get(`${API}/${id}`);
        if (dto.expectedCloseDate !== '2027-03-15') {
          throw defect(`browser in Pacific/Auckland: typed 3/15/2027, the API stored ${dto.expectedCloseDate}`);
        }
        return `Pacific/Auckland stored ${dto.expectedCloseDate}`;
      } finally {
        await session.browser.close();
      }
    }, { hypothesis: 'H-CRM-021' });

    // H-CRM-041: edit mode does `new Date('2027-06-30')` (UTC midnight), which a browser behind UTC
    // displays as the previous local day.
    await runCheck(ctx, 'deals.form.close-date-displays-in-timezone-behind-utc', async () => {
      const session = await ctx.launch({ contextOptions: { locale: 'en-US', timezoneId: 'America/Los_Angeles' } });
      try {
        const p = session.page;
        await goto(p, `/deals/${full.id}/edit`);
        await waitUntil(() => readField(formScope(p), field('Deal Name')), (v) => v === full.name, { describe: (v) => `Deal Name holds "${v}"` });
        const shown = await readField(formScope(p), field('Expected Close Date'));
        if (shown !== '6/30/2027') throw defect(`browser in America/Los_Angeles: the API holds 2027-06-30, the edit form shows ${shown}`);
        return `America/Los_Angeles shows ${shown}`;
      } finally {
        await session.browser.close();
      }
    }, { hypothesis: 'H-CRM-041' });

    await runCheck(ctx, 'deals.form.autocomplete-pick-persists', async () => {
      const id = await createThroughUi(page, { 'Deal Name': uniqueName(runId, 'deal'), Contact: contact.lastName, Organization: org.name });
      const dto = await api.get(`${API}/${id}`);
      if (dto.primaryContactId !== contact.id) throw new Error(`primaryContactId is ${dto.primaryContactId}, picked ${contact.id}`);
      if (dto.organizationId !== org.id) throw new Error(`organizationId is ${dto.organizationId}, picked ${org.id}`);
      return `primaryContactId=${dto.primaryContactId}, organizationId=${dto.organizationId}`;
    });

    // H-CRM-024: typed-but-not-picked autocomplete text is dropped without a warning.
    await runCheck(ctx, 'deals.form.organization-typed-not-picked', async () => {
      await goto(page, '/deals/new');
      await fillField(page, formScope(), field('Deal Name'), uniqueName(runId, 'deal'));
      const responded = page.waitForResponse((r) => /organizations\/search/.test(r.url()), { timeout: 8000 }).catch(() => null);
      await controlLocator(formScope(), field('Organization')).fill(org.name);
      await responded;
      await page.keyboard.press('Escape');
      await settleOverlays(page);
      await formScope().getByRole('button', { name: 'Create', exact: true }).click();
      await page.waitForURL(DETAIL_URL, { timeout: 15000 });
      const id = page.url().match(DETAIL_URL)[1];
      api.track('deal', id, `${API}/${id}`);
      const dto = await api.get(`${API}/${id}`);
      if (dto.organizationId == null) {
        throw defect(`typed "${org.name}" (an exact match) without picking it: saved with organizationId=null and no warning`);
      }
    }, { hypothesis: 'H-CRM-024' });

    // H-CRM-035: edit mode patches *names* into controls whose displayWith expects DTOs.
    await runCheck(ctx, 'deals.form.edit-shows-linked-records', async () => {
      await goto(page, `/deals/${full.id}/edit`);
      await waitUntil(() => readField(formScope(), field('Deal Name')), (v) => v === full.name, { describe: (v) => `Deal Name holds "${v}"` });
      await page.waitForTimeout(500);
      const shownContact = await readField(formScope(), field('Contact'));
      const shownOrg = await readField(formScope(), field('Organization'));
      if (shownContact !== contactName || shownOrg !== org.name) {
        throw defect(`deal is linked to "${contactName}" / "${org.name}" but the edit form shows Contact="${shownContact}", Organization="${shownOrg}"`);
      }
    }, { hypothesis: 'H-CRM-035' });
  },
};
