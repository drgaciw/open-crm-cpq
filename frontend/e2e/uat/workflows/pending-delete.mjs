import { approvalSubmitBody } from '../../lib/approval-submit.mjs';
import assert from 'node:assert/strict';
import { BASE, goto } from '../../lib/browser.mjs';
import { blocked } from '../../lib/health.mjs';
import { confirmMatDialog } from '../../lib/dialogs.mjs';
import { draftWithLine, openDetail } from './quote-lifecycle.mjs';

const PENDING = '/api/v1/quotes/approvals/pending';
const rows = (body) => Array.isArray(body) ? body : body?.content ?? [];

export function requireIsolatedSessions(ctx) {
  const origin = new URL(ctx.base ?? BASE);
  if (!ctx.isolatedFixtures || !['localhost', '127.0.0.1', '[::1]'].includes(origin.hostname)) {
    throw blocked('DRG-758 requires an explicitly isolated loopback test stack');
  }
  if (!ctx.approver?.page || ctx.page.context() === ctx.approver.page.context()
      || !ctx.sellerActor || !ctx.approverActor || ctx.sellerActor === ctx.approverActor) {
    throw blocked('DRG-758 requires separate authenticated seller and approver sessions; use e2e/uat-pending-delete.mjs');
  }
}

/** Exact browser response matcher: never accept an API seed call or another quote's DELETE. */
export function matchesDelete(response, quoteId, origin = BASE) {
  const url = new URL(response.url());
  return url.origin === new URL(origin).origin && url.pathname === `/api/v1/quotes/${quoteId}`
    && response.request().method() === 'DELETE';
}

/** Seed only fresh fixtures, then drive the actual seller confirmation and separate approver inbox. */
export async function pendingDeleteRefusal(ctx, { seed = draftWithLine } = {}) {
  requireIsolatedSessions(ctx);
  const { page, api, approver } = ctx;
  for (const key of ['quote', 'product', 'pricebook', 'pricing', 'approvalsBff']) {
    if (ctx.preflight?.[key]?.state !== 'up') throw blocked(`DRG-758 backend ${key} is unavailable`);
  }
  const { quote } = await seed(ctx);
  assert.ok(quote.name.startsWith(`UAT-${ctx.runId}-`), 'only this run’s synthetic quote may be used');
  const path = `/api/v1/quotes/${quote.id}`;
  // Submit returns the canonical routing id (ApprovalService.submitForApproval single-writer contract).
  const original = await api.post(`${path}/approvals`, await approvalSubmitBody((path) => api.get(path), quote.id, 'DEAL_DESK'));
  assert.ok(original.id != null, 'submit must return its canonical routing ID');
  assert.equal(String(original.quoteId), String(quote.id));
  assert.equal(original.status, 'PENDING');
  assert.equal(original.requestedBy, ctx.sellerActor, 'session must belong to the configured synthetic seller');
  const findOriginal = async () => rows(await approver.api.get(PENDING)).find((r) => r.id === original.id);
  const requestBefore = await findOriginal();
  assert.ok(requestBefore, 'canonical request must be visible to the separate approver');
  assert.equal(requestBefore.status, 'PENDING');
  assert.equal(String(requestBefore.quoteId), String(quote.id));
  const before = await api.get(path);
  assert.equal(before.status, 'PENDING_APPROVAL');

  await openDetail(page, quote.id);
  await page.getByRole('button', { name: 'More quote actions', exact: true }).click();
  await page.getByRole('menuitem', { name: 'Delete', exact: true }).click();
  // Subscribe after opening the dialog, before the actual confirmation click. There is no api.del here.
  const responsePromise = page.waitForResponse((r) => matchesDelete(r, quote.id, ctx.base ?? BASE), { timeout: 15000 });
  const [response] = await Promise.all([
    responsePromise,
    confirmMatDialog(page, { title: 'Delete quote', message: quote.name, click: 'Delete' }),
  ]);
  assert.equal(response.status(), 409);
  const refusal = await response.json();
  assert.equal(refusal.errorCode, 'QUOTE_APPROVAL_PENDING');
  assert.equal(refusal.message, 'Cannot delete a quote while approval is pending');
  const banner = page.getByRole('alert').filter({ hasText: 'QUOTE_APPROVAL_PENDING' });
  await banner.waitFor({ state: 'visible' });
  assert.ok((await banner.innerText()).includes(refusal.message));
  assert.equal(new URL(page.url()).pathname, `/quotes/${quote.id}`);
  assert.deepEqual(await api.get(path), before, 'refused click must not change any quote field');
  assert.deepEqual(await findOriginal(), requestBefore, 'same canonical request must stay unchanged and pending');

  await goto(approver.page, '/approvals');
  await approver.page.getByRole('heading', { level: 1, name: 'Approvals', exact: true }).waitFor({ state: 'visible' });
  const row = approver.page.getByRole('row')
    .filter({ has: approver.page.getByRole('cell', { name: String(original.id), exact: true }) })
    .filter({ has: approver.page.getByRole('link', { name: `Quote ${quote.id}`, exact: true }) });
  await row.waitFor({ state: 'visible' });
  await row.getByRole('link', { name: `Quote ${quote.id}`, exact: true }).click();
  await approver.page.waitForURL((url) => url.pathname === `/quotes/${quote.id}`);
  await approver.page.getByRole('heading', { level: 1, name: quote.name, exact: true }).waitFor({ state: 'visible' });
  assert.equal(new URL(page.url()).pathname, `/quotes/${quote.id}`, 'seller must still be on the same quote');
  return `seller confirmed Delete: 409 QUOTE_APPROVAL_PENDING; quote ${quote.id} and canonical request ${original.id} unchanged; separate approver opened the quote from its pending row`;
}
