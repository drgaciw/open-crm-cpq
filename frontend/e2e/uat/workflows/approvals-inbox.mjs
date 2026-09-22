/**
 * Approvals inbox workflows: Refresh, table-or-empty-state, and the per-row Approve / Reject /
 * Open quote flows.
 *
 * A pending approval only exists after a quote was submitted *and* approval-routing (:8085)
 * created a request for it. Rows are never seeded around the API, so while no row exists the row
 * flows record BLOCKED with that reason; they are complete and run as soon as a row appears.
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, runSteps, defect, blocked, waitUntil } from '../../lib/health.mjs';
import { withNativeDialog, confirmMatDialog } from '../../lib/dialogs.mjs';
import { inbox } from '../specs/approvals.mjs';

const PENDING = '/api/v1/quotes/approvals/pending';
const NO_ROWS = 'no pending approvals can exist while approval-routing is down';

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
  const rows = asRows(await ctx.api.get(PENDING));
  if (!rows.length) throw blocked(NO_ROWS);
  await openInbox(ctx.page);
  const row = ctx.page.getByRole('row').filter({ hasText: `Quote ${rows[0].quoteId}` }).first();
  await row.waitFor({ state: 'visible' });
  return { row, request: rows[0] };
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

  // H-CPQ-Q-005 (inbox half): window.prompt fires first, the Material confirm only afterwards —
  // the user types a reason before being asked whether they meant to reject at all.
  await runCheck(ctx, 'approvals.row.reject.prompt-before-confirm', async () => {
    const { row } = await firstRowOrBlocked(ctx);
    const seen = await withNativeDialog(page,
      { type: 'prompt', message: reject.confirm.message, accept: true, promptText: 'UAT order probe' },
      () => row.getByRole('button', { name: 'Reject', exact: true }).click());
    const dialog = page.getByRole('dialog').filter({ hasText: reject.opensDialog });
    await dialog.waitFor({ state: 'visible' });
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click(); // leave the request pending
    await dialog.waitFor({ state: 'hidden' });
    throw defect(`native prompt "${seen.message}" is raised before the "${reject.opensDialog}" confirm dialog`);
  }, { hypothesis: 'H-CPQ-Q-005' });

  await runCheck(ctx, 'approvals.row.reject.prompt-cancel-aborts', async () => {
    const { row, request } = await firstRowOrBlocked(ctx);
    await withNativeDialog(page, { type: 'prompt', message: reject.confirm.message, accept: false },
      () => row.getByRole('button', { name: 'Reject', exact: true }).click());
    await page.waitForTimeout(400);
    if (await page.getByRole('dialog').count()) throw new Error('the confirm dialog opened although the prompt was dismissed');
    if (!(await stillPending(api, request.id))) throw new Error('request left the pending list');
  });

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
        const row = page.getByRole('row').filter({ hasText: `Quote ${state.request.quoteId}` }).first();
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
    await withNativeDialog(page, { type: 'prompt', message: reject.confirm.message, accept: true, promptText: 'UAT rejection' },
      () => row.getByRole('button', { name: 'Reject', exact: true }).click());
    await confirmMatDialog(page, { title: reject.opensDialog, message: /Reject approval #\d+ for quote \d+\?/, click: 'Reject' });
    await waitUntil(() => stillPending(api, request.id), (present) => present === false, {
      timeoutMs: 8000, intervalMs: 250, describe: () => 'request is still pending after Reject',
    });
    return `approval #${request.id} rejected`;
  });
}
