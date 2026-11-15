/**
 * Approvals inbox workflows: Refresh, table-or-empty-state, and the per-row Approve / Reject /
 * Open quote flows.
 *
 * A pending approval only exists after a quote was submitted *and* approval-routing (:8085)
 * created a request for it. Rows are never seeded around the API, so while no row exists the row
 * flows record BLOCKED with that reason; they are complete and run as soon as a row appears.
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, runSteps, defect, blocked, gap, waitUntil } from '../../lib/health.mjs';
import { confirmMatDialog } from '../../lib/dialogs.mjs';
import { inbox } from '../specs/approvals.mjs';
import { draftWithLine } from './quote-lifecycle.mjs';

const PENDING = '/api/v1/quotes/approvals/pending';
const NO_ROWS = 'no pending approval exists, and none could be routed (approval-routing or price book down)';
/** Approve and Reject each consume a pending row, so the run routes a few. */
const SEEDED = 3;

/**
 * Route quotes to tiered approval through the quote BFF's own endpoint (POST /quotes/{id}/approvals)
 * when the inbox is empty. The webapp has no action that calls it (see approvals.gap.*), so this is
 * the only way a pending row appears; nothing is written around the API.
 */
export async function ensurePendingApprovals(ctx) {
  const pending = (await decidableRows(ctx.api)).length;
  if (pending >= SEEDED) return pending;
  if (ctx.preflight?.approvalsBff?.state !== 'up' || ctx.preflight?.pricebook?.state !== 'up') return pending;
  for (let i = pending; i < SEEDED; i += 1) {
    const { quote } = await draftWithLine(ctx);
    await ctx.api.post(`/api/v1/quotes/${quote.id}/approvals`, { tier: 'DEAL_DESK' });
  }
  return (await decidableRows(ctx.api)).length;
}

/**
 * Pending requests whose quote still exists. A quote deleted while PENDING_APPROVAL (teardown does
 * this every run) leaves an approval in the inbox that can no longer be decided.
 */
async function decidableRows(api) {
  const rows = [];
  for (const request of asRows(await api.get(PENDING))) {
    if (await api.get(`/api/v1/quotes/${request.quoteId}`).then(() => true, () => false)) rows.push(request);
  }
  return rows;
}

const rowAction = (name) => inbox.actions.find((a) => a.in === 'row' && a.name === name);
const dataRows = (page) => page.locator('tr[mat-row], tr.mat-mdc-row');
const asRows = (body) => (Array.isArray(body) ? body : (body?.content ?? []));

async function openInbox(page) {
  const listed = page.waitForResponse((r) => r.url().includes(PENDING), { timeout: 15000 }).catch(() => null);
  await goto(page, inbox.path);
  await listed;
  await page.getByRole('heading', { level: 1, name: inbox.heading }).waitFor({ state: 'visible' });
  await page.getByRole('status').waitFor({ state: 'hidden', timeout: 10000 }).catch(() => {});
}

/** The first pending row, or a BLOCKED outcome when the inbox is (legitimately) empty. */
async function firstRowOrBlocked(ctx) {
  const rows = await decidableRows(ctx.api);
  if (!rows.length) throw blocked(NO_ROWS);
  await openInbox(ctx.page);
  const row = rowFor(ctx.page, rows[0]);
  await row.waitFor({ state: 'visible' });
  return { row, request: rows[0] };
}

/**
 * The inbox row of one approval request, by its ID cell and exact quote link. A hasText match on
 * "Quote 8" also matches "Quote 87", so once a row leaves the table the locator would re-resolve
 * to a different request.
 */
function rowFor(page, request) {
  return page.getByRole('row')
    .filter({ has: page.getByRole('cell', { name: String(request.id), exact: true }) })
    .filter({ has: page.getByRole('link', { name: `Quote ${request.quoteId}`, exact: true }) });
}

const stillPending = async (api, id) => asRows(await api.get(PENDING)).some((r) => r.id === id);

export async function runInboxList(ctx) {
  const { page, api } = ctx;

  await runCheck(ctx, 'approvals.list.matches-api', async () => {
    const rows = asRows(await api.get(PENDING));
    await openInbox(page);
    if (!rows.length) {
      await page.getByText(inbox.emptyState.title, { exact: true }).waitFor({ state: 'visible' });
      await page.getByText(inbox.emptyState.message, { exact: true }).waitFor({ state: 'visible' });
      if (await page.getByRole('table').count()) throw new Error('an empty inbox renders the table as well as the empty state');
      return `API holds 0 pending — empty state "${inbox.emptyState.title}"`;
    }
    await waitUntil(() => dataRows(page).count(), (n) => n === rows.length, {
      timeoutMs: 5000, describe: (n) => `table shows ${n} rows, API holds ${rows.length}`,
    });
    return `${rows.length} pending rows`;
  });

  await runCheck(ctx, 'approvals.refresh.refetches', async () => {
    await openInbox(page);
    const button = page.getByRole('button', { name: 'Refresh', exact: true });
    await waitUntil(() => button.isEnabled(), (v) => v === true, { describe: () => 'Refresh stays disabled after the list loaded' });
    const refetched = page.waitForResponse((r) => r.url().includes(PENDING) && r.request().method() === 'GET', { timeout: 10000 })
      .catch(() => null);
    await button.click();
    const response = await refetched;
    if (!response) throw new Error('Refresh did not issue GET /quotes/approvals/pending');
    if (!response.ok()) throw new Error(`Refresh -> HTTP ${response.status()}`);
    await waitUntil(() => button.isEnabled(), (v) => v === true, { describe: () => 'Refresh stays disabled after refetching' });
    return `GET ${PENDING} -> ${response.status()}`;
  });

  await runCheck(ctx, 'approvals.no-error-banner', async () => {
    await openInbox(page);
    const alerts = page.locator('.error-banner[role="alert"]');
    if (await alerts.count()) throw new Error(`error banner shown: "${(await alerts.first().innerText()).replace(/\s+/g, ' ')}"`);
  });
}

export async function runInboxRows(ctx) {
  const { page, api } = ctx;
  const approve = rowAction('Approve');
  const reject = rowAction('Reject');

  // Found by UAT 2026-11-14: the inbox can only fill through the API. No quote screen offers
  // "submit for approval" (POST /quotes/{id}/approvals), so a user cannot route a quote to a tier.
  await runCheck(ctx, 'approvals.gap.no-ui-route-to-tiered-approval', async () => {
    const { quote } = await draftWithLine(ctx);
    await goto(page, `/quotes/${quote.id}`);
    await page.getByRole('heading', { level: 1 }).first().waitFor({ state: 'visible' });
    const offered = (await page.getByRole('button').allInnerTexts()).map((t) => t.replace(/\s+/g, ' ').trim());
    const more = page.getByRole('button', { name: 'More quote actions', exact: true });
    if (await more.count()) {
      await more.click();
      offered.push(...(await page.getByRole('menuitem').allInnerTexts()).map((t) => t.replace(/\s+/g, ' ').trim()));
      await page.keyboard.press('Escape');
    }
    const route = offered.filter((t) => /(submit|request|send).*(for )?approval/i.test(t));
    if (route.length) return `the quote detail offers: ${route.join(', ')}`;
    throw gap('no quote screen calls POST /quotes/{id}/approvals; a pending tiered approval cannot be created from the webapp');
  });

  await runCheck(ctx, 'approvals.seed.route-quotes-to-tiered-approval', async () => {
    const pending = await ensurePendingApprovals(ctx);
    if (!pending) throw blocked(NO_ROWS);
    return `${pending} pending approval(s) routed through POST /quotes/{id}/approvals`;
  });

  await runCheck(ctx, 'approvals.row.open-quote', async () => {
    const { row, request } = await firstRowOrBlocked(ctx);
    await row.getByRole('button', { name: 'Open quote', exact: true }).click();
    await page.waitForURL((url) => url.pathname === `/quotes/${request.quoteId}`);
  });

  await runCheck(ctx, 'approvals.row.quote-link', async () => {
    const { row, request } = await firstRowOrBlocked(ctx);
    await row.getByRole('link', { name: `Quote ${request.quoteId}`, exact: true }).click();
    await page.waitForURL((url) => url.pathname === `/quotes/${request.quoteId}`);
  });

  // H-CPQ-Q-005 (fixed, UAT 2026-11-14): Reject used to raise window.prompt for the reason and
  // only then a Material confirm. One "Reject request" dialog now carries the optional reason.
  await runCheck(ctx, 'approvals.row.reject.single-dialog', async () => {
    const { row, request } = await firstRowOrBlocked(ctx);
    const native = [];
    const onDialog = (d) => { native.push(`${d.type()}: ${d.message()}`); void d.dismiss(); };
    page.on('dialog', onDialog);
    try {
      await row.getByRole('button', { name: 'Reject', exact: true }).click();
      const dialog = page.getByRole('dialog').filter({ hasText: reject.opensDialog });
      await dialog.waitFor({ state: 'visible', timeout: 4000 }).catch(() => {});
      if (native.length) throw defect(`native ${native.join('; ')} is raised by Reject`);
      if (!(await dialog.isVisible())) throw new Error(`no "${reject.opensDialog}" dialog opened`);
      await dialog.getByRole('textbox', { name: 'Reason (optional)' }).waitFor({ state: 'visible' });
      const said = (await dialog.innerText()).replace(/\s+/g, ' ');
      if (!said.includes(`Reject approval #${request.id} for quote ${request.quoteId}?`)) {
        throw new Error(`dialog does not name the approval: "${said.slice(0, 120)}"`);
      }
      await dialog.getByRole('button', { name: 'Cancel', exact: true }).click();
      await dialog.waitFor({ state: 'hidden' });
    } finally {
      page.off('dialog', onDialog);
    }
    if (!(await stillPending(api, request.id))) throw new Error('Cancel rejected the request');
    return 'one dialog with an optional reason; Cancel leaves the request pending';
  }, { hypothesis: 'H-CPQ-Q-005' });

  const state = { request: null };
  await runSteps(ctx, [
    {
      id: 'approvals.row.approve.cancel-keeps-pending',
      run: async () => {
        const { row, request } = await firstRowOrBlocked(ctx);
        state.request = request;
        await row.getByRole('button', { name: 'Approve', exact: true }).click();
        await confirmMatDialog(page, { title: approve.confirm.title, message: approve.confirm.message, click: approve.confirm.cancelText });
        await page.waitForTimeout(400);
        if (!(await stillPending(api, request.id))) throw new Error('request left the pending list');
      },
    },
    {
      id: 'approvals.row.approve.confirm',
      run: async () => {
        const row = rowFor(page, state.request);
        await row.getByRole('button', { name: 'Approve', exact: true }).click();
        await confirmMatDialog(page, { title: approve.confirm.title, message: approve.confirm.message, click: approve.confirm.confirmText });
        await waitUntil(() => stillPending(api, state.request.id), (present) => present === false, {
          timeoutMs: 8000, intervalMs: 250, describe: () => 'request is still pending after Approve',
        });
        await row.waitFor({ state: 'hidden', timeout: 8000 });
        return `approval #${state.request.id} left the inbox`;
      },
    },
  ]);

  await runCheck(ctx, 'approvals.row.reject.confirm', async () => {
    const { row, request } = await firstRowOrBlocked(ctx);
    await row.getByRole('button', { name: 'Reject', exact: true }).click();
    const dialog = page.getByRole('dialog').filter({ hasText: reject.opensDialog });
    await dialog.getByRole('textbox', { name: 'Reason (optional)' }).fill('UAT rejection');
    await dialog.getByRole('button', { name: 'Reject', exact: true }).click();
    await dialog.waitFor({ state: 'hidden' });
    await waitUntil(() => stillPending(api, request.id), (present) => present === false, {
      timeoutMs: 8000, intervalMs: 250, describe: () => 'request is still pending after Reject',
    });

  // Found by UAT 2026-11-14: deleting a quote in PENDING_APPROVAL leaves its approval in the inbox.
  // Deciding it used to record the decision in approval-routing and then fail with 404; now it is
  // refused first and the request stays pending. Whether delete should be refused is DRG-758.
  await runCheck(ctx, 'approvals.orphan.decision-refused-before-routing', async () => {
    if (ctx.preflight?.approvalsBff?.state !== 'up' || ctx.preflight?.pricebook?.state !== 'up') throw blocked(NO_ROWS);
    const { quote } = await draftWithLine(ctx);
    await api.post(`/api/v1/quotes/${quote.id}/approvals`, { tier: 'DEAL_DESK' });
    const request = asRows(await api.get(PENDING)).find((r) => r.quoteId === quote.id);
    if (!request) throw blocked(`the approval routed for quote ${quote.id} is not pending`);
    await api.del(`/api/v1/quotes/${quote.id}`);
    api.untrack('quote', quote.id);
    await openInbox(page);
    const row = rowFor(page, request);
    await row.getByRole('button', { name: 'Approve', exact: true }).click();
    await confirmMatDialog(page, { title: approve.confirm.title, message: approve.confirm.message, click: approve.confirm.confirmText });
    const banner = page.getByRole('alert').filter({ hasText: 'RESOURCE_NOT_FOUND' });
    await banner.waitFor({ state: 'visible', timeout: 8000 });
    const said = (await banner.innerText()).replace(/\s+/g, ' ').trim();
    if (!(await stillPending(api, request.id))) {
      throw defect(`approval-routing recorded the decision on #${request.id} although quote ${quote.id} is deleted; the UI said "${said}"`);
    }
    await row.waitFor({ state: 'visible' });
    return `approval #${request.id} stays pending; banner "${said.slice(0, 80)}"`;
  });
    return `approval #${request.id} rejected`;
  });
}
