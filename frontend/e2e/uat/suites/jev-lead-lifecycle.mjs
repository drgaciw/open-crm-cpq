/**
 * Model-driven lead lifecycle. Jev is given a goal and the buttons on /leads/{id}; it has to
 * DISCOVER Mark Contacted -> Qualify -> Convert to Deal. Success is proven through the API.
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck } from '../../lib/health.mjs';
import { ensure } from '../fixtures.mjs';
import { driveGoal, observeButtons, tracedDecider, summarizeTrace, DEFAULT_FORBIDDEN, LOSE_ACTIONS } from '../runners/jevDriver.mjs';

const GOAL = 'Take this lead through the sales process until it has been converted to a deal.';
const EXPECTED_PATH = ['Mark Contacted', 'Qualify', 'Convert to Deal'];
// Losing a lead opens a dialog with a required free-text reason; never part of this goal.
const FORBIDDEN = [...DEFAULT_FORBIDDEN, ...LOSE_ACTIONS];

export default {
  id: 'jev-lead-lifecycle',
  backend: 'crm',
  jev: true,

  async run(ctx) {
    const { page, api } = ctx;
    const decider = await tracedDecider(ctx);
    let run;

    await runCheck(ctx, 'jev-lead-lifecycle.converts-lead', async () => {
      const lead = await ensure(ctx, 'lead');
      await goto(page, `/leads/${lead.id}`);
      await page.getByRole('heading', { name: lead.title, exact: true }).waitFor();

      const fetchLead = () => api.get(`/api/v1/leads/${lead.id}`);
      let tracked = false;
      const isDone = async () => {
        const current = await fetchLead();
        if (current.convertedDealId != null && !tracked) {
          tracked = true;
          api.track('deal', current.convertedDealId, `/api/v1/deals/${current.convertedDealId}`);
        }
        return current.status === 'CONVERTED' && current.convertedDealId != null;
      };

      try {
        run = await driveGoal(ctx, {
          goal: GOAL,
          decider,
          isDone,
          forbidden: FORBIDDEN,
          observe: (p) => observeButtons(p, { statusSelector: 'main .detail-header mat-chip' }),
        });
      } finally {
        await isDone().catch(() => {}); // make sure a half-finished run still ledgers its deal
      }

      const final = await fetchLead();
      if (final.status !== 'CONVERTED' || final.convertedDealId == null) {
        throw new Error(`API says status=${final.status} convertedDealId=${final.convertedDealId}`);
      }
      const leaked = run.offeredEver.filter((name) => FORBIDDEN.some((p) => p.test(name)));
      if (leaked.length) throw new Error(`forbidden buttons were offered to the model: ${leaked.join(', ')}`);
      if (JSON.stringify(run.path) !== JSON.stringify(EXPECTED_PATH)) {
        throw new Error(`lead was converted, but by an unexpected path: [${run.path.join(' > ')}]`);
      }
      const confirm = run.trace.find((t) => t.button === 'Convert to Deal')?.nativeDialog;
      if (!confirm) throw new Error('Convert to Deal raised no native confirm');
      return `lead ${lead.id} -> deal ${final.convertedDealId} | ${summarizeTrace(run.trace)}`;
    });

    if (run) ctx.report.info('jev-lead-lifecycle.trace', JSON.stringify(run.trace.map(({ probabilities, ...t }) => t)));
    ctx.report.info('jev-lead-lifecycle.usage', JSON.stringify({ model: decider.model, transport: decider.transport, ...decider.totals(), calls: decider.calls }));
  },
};
