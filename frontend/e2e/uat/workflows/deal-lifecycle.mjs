/**
 * Deal lifecycle on the detail page:
 *   PROSPECTING -> Advance Stage x4 -> NEGOTIATION (Advance disappears) -> Won (native confirm)
 *   PROSPECTING -> Lost (the "Mark Deal as Lost" dialog, Reason required)
 *
 * Every transition is proven through the API (`GET /api/v1/deals/{id}`), and the visible header
 * actions are asserted in every stage from the `visibleWhen` lists of the detail spec.
 *
 * Check ids: `deals.lifecycle.<step>` and `deals.lost.<step>`
 */
import { BASE, goto } from '../../lib/browser.mjs';
import { runSteps, waitUntil, blocked } from '../../lib/health.mjs';
import { withNativeDialog } from '../../lib/dialogs.mjs';
import { expectNoSnackbar } from '../../lib/overlays.mjs';
import { scopeLocator, controlLocator } from '../../lib/fields.mjs';
import { ensure } from '../fixtures.mjs';
import { STAGES, stageLabel } from '../specs/deals.mjs';
import { blockedReason } from '../../lib/preflight.mjs';
import { draftWithLine } from './quote-lifecycle.mjs';

const API = '/api/v1/deals';

const button = (page, name) => page.getByRole('button', { name, exact: true }).first();

/** Load the detail page and wait until its stage badge shows the expected stage. */
export async function openDeal(page, deal, stage) {
  await goto(page, `/deals/${deal.id}`);
  await page.getByRole('heading', { level: 1 }).filter({ hasText: deal.name }).waitFor({ state: 'visible' });
  if (stage) await expectBadge(page, stage);
}

export async function expectBadge(page, stage) {
  const badge = page.locator('.detail-header .stage-badge').first();
  await waitUntil(async () => ((await badge.innerText().catch(() => '')) ?? '').trim(), (text) => text === stageLabel(stage), {
    timeoutMs: 8000,
    describe: (text) => `stage badge shows "${text}", expected "${stageLabel(stage)}"`,
  });
}

/** Poll the API until the deal reaches `stage`; returns the DTO. */
export async function expectApiStage(api, id, stage) {
  return waitUntil(() => api.get(`${API}/${id}`), (dto) => dto?.stage === stage, {
    timeoutMs: 8000,
    describe: (dto) => `API stage is ${dto?.stage}, expected ${stage}`,
  });
}

export const WON_NEEDS_ACCEPTED_QUOTE =
  'A Won fixture requires an accepted quote and an isolated local CRM/CPQ stack with quote event delivery enabled';

export function canSeedWon(ctx) {
  return process.env.UAT_ISOLATED_FIXTURES === '1'
    && ['localhost', '127.0.0.1', '[::1]'].includes(new URL(BASE).hostname)
    && ['quote', 'product', 'pricing', 'pricebook', 'configValidate'].every(key => !blockedReason(ctx.preflight, key));
}

export async function acceptQuoteForDeal(ctx, deal) {
  if (!canSeedWon(ctx)) throw blocked(WON_NEEDS_ACCEPTED_QUOTE);
  const { quote } = await draftWithLine(ctx, { dealExternalId: deal.uuid });
  for (const transition of ['calculate', 'submit', 'approve', 'send', 'accept']) {
    await ctx.api.post(`/api/v1/quotes/${quote.id}/${transition}`);
  }
  return expectApiStage(ctx.api, deal.id, 'WON');
}

/** After a confirmed Won: the refusal is shown in an alert and the stage is unchanged. */
export async function expectWinRefused(page, api, id, stage) {
  // Banner copy: "…until a linked quote is Accepted… [OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE]".
  const alert = page.getByRole('alert').filter({ hasText: /OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE|quote is accepted|accepted quote/i }).first();
  await alert.waitFor({ state: 'visible', timeout: 8000 });
  const dto = await api.get(`${API}/${id}`);
  if (dto.stage !== stage) throw new Error(`stage became ${dto.stage}; a win without an accepted quote must be refused`);
  return `refused with "${(await alert.innerText()).replace(/\s+/g, ' ').trim()}"; stage stays ${stage}`;
}

/** Compare rendered header actions with what the spec declares for `stage`. */
export async function assertActionsFor(page, detail, stage) {
  const problems = [];
  for (const action of detail.actions.filter((a) => a.in === 'header')) {
    const visible = await button(page, action.name).isVisible();
    const expected = action.visibleWhen ? action.visibleWhen.includes(stage) : true;
    if (visible !== expected) problems.push(`"${action.name}" is ${visible ? 'visible' : 'hidden'} in ${stage}`);
  }
  if (problems.length) throw new Error(problems.join('; '));
  return `${stage}: ${detail.actions.filter((a) => !a.visibleWhen || a.visibleWhen.includes(stage)).map((a) => a.name).join(', ')}`;
}

/**
 * @param {object} ctx
 * @param {import('../contract.mjs').PageSpec} detail  The deal detail spec.
 * @returns {Promise<{won: object, lost: object}>} The two fixture deals, for follow-up checks.
 */
export async function runDealLifecycle(ctx, detail) {
  const { page, api } = ctx;
  const wonAction = detail.actions.find((a) => a.name === 'Won');
  const loseForm = detail.forms.find((f) => f.id === 'lose-reason');
  const reasonField = loseForm.fields[0];
  const pipeline = STAGES.map((s) => s.value).slice(0, 5); // PROSPECTING … NEGOTIATION

  // ── PROSPECTING -> … -> NEGOTIATION -> WON ────────────────────────────────────
  const deal = await ensure(ctx, 'deal');
  let lastProbability = 10;
  let won = null;

  const steps = [{
    id: 'deals.lifecycle.PROSPECTING.actions',
    run: async () => {
      await openDeal(page, deal, 'PROSPECTING');
      return assertActionsFor(page, detail, 'PROSPECTING');
    },
  }];

  for (let i = 1; i < pipeline.length; i++) {
    const from = pipeline[i - 1];
    const to = pipeline[i];
    steps.push({
      id: `deals.lifecycle.advance.${from}-to-${to}`,
      run: async () => {
        await openDeal(page, deal, from);
        await button(page, 'Advance Stage').click();
        const dto = await expectApiStage(api, deal.id, to);
        await expectBadge(page, to);
        await expectNoSnackbar(page);
        if (!(dto.probability > lastProbability)) {
          throw new Error(`probability did not rise with the stage: ${lastProbability}% -> ${dto.probability}%`);
        }
        const note = `${stageLabel(to)}, probability ${lastProbability}% -> ${dto.probability}%`;
        lastProbability = dto.probability;
        return note;
      },
    });
    steps.push({
      id: `deals.lifecycle.${to}.actions`,
      run: async () => {
        await openDeal(page, deal, to);
        return assertActionsFor(page, detail, to);
      },
    });
  }

  steps.push(
    {
      id: 'deals.lifecycle.won-cancel-keeps-stage',
      run: async () => {
        await openDeal(page, deal, 'NEGOTIATION');
        await withNativeDialog(page, { type: 'confirm', message: wonAction.confirm.message, accept: false },
          () => button(page, 'Won').click());
        await page.waitForTimeout(500);
        const dto = await api.get(`${API}/${deal.id}`);
        if (dto.stage !== 'NEGOTIATION') throw new Error(`stage became ${dto.stage} although the confirm was cancelled`);
      },
    },
    {
      id: 'deals.lifecycle.won-requires-accepted-quote',
      run: async () => {
        await openDeal(page, deal, 'NEGOTIATION');
        await withNativeDialog(page, { type: 'confirm', message: wonAction.confirm.message },
          () => button(page, 'Won').click());
        return expectWinRefused(page, api, deal.id, 'NEGOTIATION');
      },
    },
    {
      id: 'deals.lifecycle.WON.actions',
      run: async () => {
        won = await acceptQuoteForDeal(ctx, deal);
        await openDeal(page, won, 'WON');
        return assertActionsFor(page, detail, 'WON');
      },
    },
  );

  await runSteps(ctx, steps);

  // ── PROSPECTING -> LOST through the reason dialog ─────────────────────────────
  const loser = await ensure(ctx, 'deal');
  const reason = String(reasonField.sample);
  const dialog = () => scopeLocator(page, loseForm.scope);

  await runSteps(ctx, [
    {
      id: 'deals.lost.dialog-opens-gated',
      run: async () => {
        await openDeal(page, loser, 'PROSPECTING');
        await button(page, 'Lost').click();
        await dialog().waitFor({ state: 'visible' });
        if (await dialog().getByRole('button', { name: loseForm.submit.name, exact: true }).isEnabled()) {
          throw new Error(`"${loseForm.submit.name}" is enabled before a reason is entered`);
        }
      },
    },
    {
      id: 'deals.lost.cancel-keeps-stage',
      run: async () => {
        await openDeal(page, loser, 'PROSPECTING');
        await button(page, 'Lost').click();
        await controlLocator(dialog(), reasonField).fill('never submitted');
        await dialog().getByRole('button', { name: loseForm.cancel.name, exact: true }).click();
        await dialog().waitFor({ state: 'hidden' });
        await page.waitForTimeout(400);
        const dto = await api.get(`${API}/${loser.id}`);
        if (dto.stage !== 'PROSPECTING') throw new Error(`stage became ${dto.stage} although the dialog was cancelled`);
      },
    },
    {
      id: 'deals.lost.submit',
      run: async () => {
        await openDeal(page, loser, 'PROSPECTING');
        await button(page, 'Lost').click();
        await controlLocator(dialog(), reasonField).fill(reason);
        await dialog().getByRole('button', { name: loseForm.submit.name, exact: true }).click();
        await dialog().waitFor({ state: 'hidden' });
        const dto = await expectApiStage(api, loser.id, 'LOST');
        if (dto.lostReason !== reason) throw new Error(`API lostReason is "${dto.lostReason}", typed "${reason}"`);
        await expectBadge(page, 'LOST');
        await expectNoSnackbar(page);
      },
    },
    {
      id: 'deals.lost.actions-and-reason-card',
      run: async () => {
        await openDeal(page, loser, 'LOST');
        const summary = await assertActionsFor(page, detail, 'LOST');
        await scopeLocator(page, { kind: 'card', title: 'Lost Reason' }).getByText(reason, { exact: true }).waitFor({ state: 'visible' });
        return summary;
      },
    },
  ]);

  return { won, lost: loser };
}
