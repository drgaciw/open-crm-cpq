/**
 * Model-driven deal progression. Expected discovery: Advance Stage x4 (the button disappears at
 * NEGOTIATION) and then Won (native confirm). Won is available at every open stage, so closing
 * early is a legitimate shortcut: only the end state and the safety envelope are asserted, and the
 * path the model actually took is reported.
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck } from '../../lib/health.mjs';
import { ensure } from '../fixtures.mjs';
import { driveGoal, observeButtons, tracedDecider, summarizeTrace, DEFAULT_FORBIDDEN, LOSE_ACTIONS } from '../runners/jevDriver.mjs';

const GOAL = 'Progress this deal through every stage and close it as won.';
const FORBIDDEN = [...DEFAULT_FORBIDDEN, ...LOSE_ACTIONS];

export default {
  id: 'jev-deal-won',
  backend: 'crm',
  jev: true,

  async run(ctx) {
    const { page, api } = ctx;
    const decider = await tracedDecider(ctx);
    let run;
    const stages = [];

    await runCheck(ctx, 'jev-deal-won.closes-deal-as-won', async () => {
      const deal = await ensure(ctx, 'deal');
      await goto(page, `/deals/${deal.id}`);
      await page.getByRole('heading', { name: deal.name, exact: true }).waitFor();

      const stage = async () => (await api.get(`/api/v1/deals/${deal.id}`)).stage;
      stages.push(await stage());

      run = await driveGoal(ctx, {
        goal: GOAL,
        decider,
        isDone: async () => (await stage()) === 'WON',
        forbidden: FORBIDDEN,
        maxSteps: 8,
        observe: (p) => observeButtons(p, { statusSelector: 'main .detail-header .stage-badge' }),
        onAct: async () => {
          // Ground-truth stage after each press, so the report shows what each click really did.
          await page.waitForLoadState('networkidle').catch(() => {});
          stages.push(await stage());
        },
      });

      const final = await stage();
      if (final !== 'WON') throw new Error(`API says stage=${final}`);
      const leaked = run.offeredEver.filter((name) => FORBIDDEN.some((p) => p.test(name)));
      if (leaked.length) throw new Error(`forbidden buttons were offered to the model: ${leaked.join(', ')}`);
      if (!run.trace.find((t) => t.button === 'Won')?.nativeDialog) throw new Error('Won raised no native confirm');

      const advances = run.path.filter((name) => name === 'Advance Stage').length;
      const shape = advances === 4 ? 'full path' : `SHORTCUT: closed after ${advances} advance(s)`;
      return `deal ${deal.id} ${shape} [${run.path.join(' > ')}] stages ${stages.join('>')} | ${summarizeTrace(run.trace)}`;
    });

    if (run) ctx.report.info('jev-deal-won.trace', JSON.stringify(run.trace.map(({ probabilities, ...t }) => t)));
    ctx.report.info('jev-deal-won.usage', JSON.stringify({ model: decider.model, transport: decider.transport, ...decider.totals(), calls: decider.calls }));
  },
};
