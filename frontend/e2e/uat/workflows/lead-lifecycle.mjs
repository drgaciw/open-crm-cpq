/**
 * Lead lifecycle on the detail page:
 *   NEW -> Mark Contacted -> Qualify -> Convert to Deal (native confirm, lands on /deals/:id)
 *   NEW -> Mark Lost (the "Mark Lead as Lost" dialog, Reason required)
 *
 * Every transition is proven through the API (`GET /api/v1/leads/{id}`), never through the UI's
 * own optimistic state, and the set of visible action buttons is asserted in every status from
 * the `visibleWhen` lists of the detail spec.
 *
 * Check ids: `leads.lifecycle.<step>` and `leads.lost.<step>`
 */
import { goto } from '../../lib/browser.mjs';
import { runSteps, waitUntil } from '../../lib/health.mjs';
import { withNativeDialog } from '../../lib/dialogs.mjs';
import { expectNoSnackbar } from '../../lib/overlays.mjs';
import { scopeLocator, controlLocator } from '../../lib/fields.mjs';
import { ensure } from '../fixtures.mjs';
import { BANNERS } from '../specs/leads.mjs';

const API = '/api/v1/leads';

const button = (page, name) => page.getByRole('button', { name, exact: true }).first();

/** Load the detail page and wait until it shows the expected status chip. */
export async function openLead(page, lead, status) {
  await goto(page, `/leads/${lead.id}`);
  await page.getByRole('heading', { level: 1 }).filter({ hasText: lead.title }).waitFor({ state: 'visible' });
  if (status) await expectChip(page, status);
}

/** The header chip renders the raw enum today (H-CRM-020), so compare case-insensitively. */
export async function expectChip(page, status) {
  const chip = page.locator('.detail-header mat-chip').first();
  await waitUntil(async () => ((await chip.innerText().catch(() => '')) ?? '').trim(), (text) => text.toUpperCase() === status, {
    timeoutMs: 8000,
    describe: (text) => `status chip shows "${text}", expected ${status}`,
  });
}

/** Poll the API until the lead reaches `status`; returns the DTO. */
export async function expectApiStatus(api, id, status) {
  return waitUntil(() => api.get(`${API}/${id}`), (dto) => dto?.status === status, {
    timeoutMs: 8000,
    describe: (dto) => `API status is ${dto?.status}, expected ${status}`,
  });
}

/** Compare rendered action buttons and banners with what the spec declares for `status`. */
export async function assertActionsFor(page, detail, status) {
  const problems = [];
  for (const action of detail.actions.filter((a) => a.visibleWhen)) {
    const visible = await button(page, action.name).isVisible();
    const expected = action.visibleWhen.includes(status);
    if (visible !== expected) problems.push(`"${action.name}" is ${visible ? 'visible' : 'hidden'} in ${status}`);
  }
  for (const [bannerStatus, text] of Object.entries(BANNERS)) {
    const visible = await page.getByText(text, { exact: true }).isVisible();
    if (visible !== (bannerStatus === status)) problems.push(`banner "${text}" is ${visible ? 'visible' : 'hidden'} in ${status}`);
  }
  if (problems.length) throw new Error(problems.join('; '));
  const shown = detail.actions.filter((a) => a.visibleWhen?.includes(status)).map((a) => a.name);
  return `${status}: ${shown.join(', ') || 'no actions'}`;
}

/**
 * @param {object} ctx
 * @param {import('../contract.mjs').PageSpec} detail  The lead detail spec.
 */
export async function runLeadLifecycle(ctx, detail) {
  const { page, api } = ctx;
  const convertAction = detail.actions.find((a) => a.name === 'Convert to Deal');
  const loseForm = detail.forms.find((f) => f.id === 'lose-reason');
  const reasonField = loseForm.fields[0];

  // ── happy path: NEW -> CONTACTED -> QUALIFIED -> CONVERTED ────────────────────
  const lead = await ensure(ctx, 'lead', { estimatedValue: 4200 });
  let dealId = null;

  await runSteps(ctx, [
    {
      id: 'leads.lifecycle.new.actions',
      run: async () => {
        await openLead(page, lead, 'NEW');
        return assertActionsFor(page, detail, 'NEW');
      },
    },
    {
      id: 'leads.lifecycle.mark-contacted',
      run: async () => {
        await openLead(page, lead, 'NEW');
        await button(page, 'Mark Contacted').click();
        await expectApiStatus(api, lead.id, 'CONTACTED');
        await expectChip(page, 'CONTACTED');
        await expectNoSnackbar(page);
      },
    },
    {
      id: 'leads.lifecycle.contacted.actions',
      run: async () => {
        await openLead(page, lead, 'CONTACTED');
        return assertActionsFor(page, detail, 'CONTACTED');
      },
    },
    {
      id: 'leads.lifecycle.qualify',
      run: async () => {
        await openLead(page, lead, 'CONTACTED');
        await button(page, 'Qualify').click();
        await expectApiStatus(api, lead.id, 'QUALIFIED');
        await expectChip(page, 'QUALIFIED');
      },
    },
    {
      id: 'leads.lifecycle.qualified.actions',
      run: async () => {
        await openLead(page, lead, 'QUALIFIED');
        return assertActionsFor(page, detail, 'QUALIFIED');
      },
    },
    {
      id: 'leads.lifecycle.convert-cancel-keeps-status',
      run: async () => {
        await openLead(page, lead, 'QUALIFIED');
        await withNativeDialog(page, { type: 'confirm', message: `Convert "${lead.title}" to a deal?`, accept: false },
          () => button(page, 'Convert to Deal').click());
        await page.waitForTimeout(500);
        const dto = await api.get(`${API}/${lead.id}`);
        if (dto.status !== 'QUALIFIED') throw new Error(`status became ${dto.status} although the confirm was cancelled`);
        if (new URL(page.url()).pathname !== `/leads/${lead.id}`) throw new Error('cancelled convert navigated away');
      },
    },
    {
      id: 'leads.lifecycle.convert',
      run: async () => {
        await openLead(page, lead, 'QUALIFIED');
        await withNativeDialog(page, { type: 'confirm', message: convertAction.confirm.message },
          () => button(page, 'Convert to Deal').click());
        await page.waitForURL(convertAction.effect.navigate, { timeout: 15000 });
        dealId = page.url().match(convertAction.effect.navigate)[1];
        api.track('deal', dealId, `/api/v1/deals/${dealId}`);
        const dto = await expectApiStatus(api, lead.id, 'CONVERTED');
        if (String(dto.convertedDealId) !== String(dealId)) {
          throw new Error(`landed on /deals/${dealId} but the lead says convertedDealId=${dto.convertedDealId}`);
        }
        return `lead ${lead.id} -> deal ${dealId}`;
      },
    },
    {
      id: 'leads.lifecycle.converted-deal-carries-lead-data',
      run: async () => {
        const deal = await api.get(`/api/v1/deals/${dealId}`);
        const problems = [];
        if (deal.name !== lead.title) problems.push(`deal name "${deal.name}" != lead title "${lead.title}"`);
        if (deal.stage !== 'PROSPECTING') problems.push(`deal stage is ${deal.stage}`);
        if (Number(deal.amount) !== 4200) problems.push(`deal amount ${deal.amount} != estimated value 4200`);
        if (String(deal.sourceLeadId) !== String(lead.id)) problems.push(`sourceLeadId is ${deal.sourceLeadId}`);
        if (problems.length) throw new Error(problems.join('; '));
        await page.getByRole('heading', { level: 1 }).filter({ hasText: lead.title }).waitFor({ state: 'visible' });
        return `deal "${deal.name}" ${deal.stage} amount ${deal.amount}`;
      },
    },
    {
      id: 'leads.lifecycle.converted.actions-and-banner',
      run: async () => {
        await openLead(page, lead, 'CONVERTED');
        const summary = await assertActionsFor(page, detail, 'CONVERTED');
        await page.getByRole('link', { name: 'View Deal' }).click();
        await page.waitForURL((url) => url.pathname === `/deals/${dealId}`);
        return `${summary}; View Deal -> /deals/${dealId}`;
      },
    },
  ]);

  // ── lost path: NEW -> LOST through the reason dialog ──────────────────────────
  const loser = await ensure(ctx, 'lead');
  const reason = String(reasonField.sample);
  const dialog = () => scopeLocator(page, loseForm.scope);

  await runSteps(ctx, [
    {
      id: 'leads.lost.dialog-opens-gated',
      run: async () => {
        await openLead(page, loser, 'NEW');
        await button(page, 'Mark Lost').click();
        await dialog().waitFor({ state: 'visible' });
        if (await dialog().getByRole('button', { name: loseForm.submit.name, exact: true }).isEnabled()) {
          throw new Error(`"${loseForm.submit.name}" is enabled before a reason is entered`);
        }
      },
    },
    {
      id: 'leads.lost.cancel-keeps-status',
      run: async () => {
        await openLead(page, loser, 'NEW');
        await button(page, 'Mark Lost').click();
        await controlLocator(dialog(), reasonField).fill('never submitted');
        await dialog().getByRole('button', { name: loseForm.cancel.name, exact: true }).click();
        await dialog().waitFor({ state: 'hidden' });
        await page.waitForTimeout(400);
        const dto = await api.get(`${API}/${loser.id}`);
        if (dto.status !== 'NEW') throw new Error(`status became ${dto.status} although the dialog was cancelled`);
      },
    },
    {
      id: 'leads.lost.submit',
      run: async () => {
        await openLead(page, loser, 'NEW');
        await button(page, 'Mark Lost').click();
        await controlLocator(dialog(), reasonField).fill(reason);
        await dialog().getByRole('button', { name: loseForm.submit.name, exact: true }).click();
        await dialog().waitFor({ state: 'hidden' });
        const dto = await expectApiStatus(api, loser.id, 'LOST');
        if (dto.lostReason !== reason) throw new Error(`API lostReason is "${dto.lostReason}", typed "${reason}"`);
        await expectChip(page, 'LOST');
        await expectNoSnackbar(page);
      },
    },
    {
      id: 'leads.lost.actions-banner-and-reason-card',
      run: async () => {
        await openLead(page, loser, 'LOST');
        const summary = await assertActionsFor(page, detail, 'LOST');
        await scopeLocator(page, { kind: 'card', title: 'Lost Reason' }).getByText(reason, { exact: true }).waitFor({ state: 'visible' });
        return summary;
      },
    },
  ]);
}
