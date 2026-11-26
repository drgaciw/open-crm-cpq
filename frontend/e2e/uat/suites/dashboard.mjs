import assert from 'node:assert/strict';
import { goto } from '../../lib/browser.mjs';
import { choice } from '../../lib/decide.mjs';
import { inconclusive, runCheck } from '../../lib/health.mjs';
import { ensure } from '../fixtures.mjs';
import { STAGES } from '../specs/deals.mjs';

const unwrap = payload => payload.success === true ? payload.data : payload;
const paths = {
  pipeline: '/api/v1/deals/pipeline',
  leads: '/api/v1/leads/stats',
};

export default {
  id: 'dashboard',
  backend: 'crm',
  async run(ctx) {
    const { page } = ctx;
    const card = title => page.locator('main mat-card').filter({ has: page.getByText(title, { exact: true }) });

    await runCheck(ctx, 'dashboard.kpis-and-pipeline', async () => {
      const responses = Object.values(paths).map(path => page.waitForResponse(response =>
        new URL(response.url()).pathname === path && response.ok()));
      await goto(page, '/dashboard');
      const [pipeline, leadCounts] = await Promise.all(responses.map(async response => unwrap(await (await response).json())));
      const sum = (rows, property) => rows.reduce((total, row) => total + (row[property] ?? 0), 0);
      const currency = pipeline.find(row => row.baseCurrency)?.baseCurrency ?? 'USD';
      const money = value => new Intl.NumberFormat('en-US', {
        style: 'currency', currency, maximumFractionDigits: 0,
      }).format(value);
      for (const [label, value] of [
        ['Total Deals', String(sum(pipeline, 'count'))],
        ['Pipeline Value', money(sum(pipeline, 'totalAmount'))],
        ['Total Leads', String(Object.values(leadCounts).reduce((total, count) => total + count, 0))],
        ['Won Value', money(sum(pipeline.filter(row => row.stage === 'WON'), 'totalAmount'))],
      ]) {
        const metric = card(label).locator('.kpi-value');
        await metric.waitFor();
        assert.equal((await metric.innerText()).trim(), value, label);
      }
      if (pipeline.length) {
        const breakdown = card('Pipeline Breakdown');
        assert.equal(await breakdown.locator('.pipeline-row').count(), STAGES.length);
        for (const { value, label } of STAGES) {
          const row = breakdown.locator('.pipeline-row').filter({ has: page.getByText(label, { exact: true }) });
          const stage = pipeline.find(item => item.stage === value);
          assert.equal((await row.locator('.stage-stats').innerText()).trim(),
            `${stage?.count ?? 0} deals · ${money(stage?.totalAmount ?? 0)}`);
        }
      }
      return 'four KPI cards and seven pipeline stage counts/amounts matched the live responses';
    });

    await runCheck(ctx, 'dashboard.recent-activity-links', async () => {
      const lead = await ensure(ctx, 'lead');
      const deal = await ensure(ctx, 'deal');
      for (const [title, label, path, id] of [
        ['Recent Deals', deal.name, '/deals', deal.id],
        ['Recent Leads', lead.title, '/leads', lead.id],
      ]) {
        await goto(page, '/dashboard');
        await card(title).getByRole('link', { name: label, exact: true }).click();
        await page.waitForURL(url => url.pathname === path + '/' + id);
        await goto(page, '/dashboard');
        await card(title).getByRole('link', { name: 'View All', exact: true }).click();
        await page.waitForURL(url => url.pathname === path);
      }
      return 'both card links opened the seeded detail and both View All links opened the lists';
    });

    await runCheck(ctx, 'dashboard.retry-recovers', async () => {
      const pattern = '**/api/v1/deals/pipeline';
      await page.route(pattern, route => route.fulfill({
        status: 503, json: { success: false, message: 'UAT dashboard response unavailable' },
      }), { times: 1 });
      try {
        await goto(page, '/dashboard');
        await page.locator('main [role="alert"]').getByText('UAT dashboard response unavailable').waitFor();
        const retry = page.waitForResponse(response =>
          new URL(response.url()).pathname === paths.pipeline && response.ok());
        await page.getByRole('button', { name: 'Retry', exact: true }).click();
        await retry;
        await card('Total Deals').waitFor();
        assert.equal(await page.locator('main [role="alert"]').count(), 0);
        return 'Retry recovered the dashboard after one HTTP 503';
      } finally {
        await page.unroute(pattern);
      }
    }, { expectedErrors: [/^503 GET \/api\/v1\/deals\/pipeline$/] });

    if (ctx.useJev) {
      await runCheck(ctx, 'dashboard.jev-card-choice', async () => {
        await goto(page, '/dashboard');
        const routes = { deals: 'Recent Deals', leads: 'Recent Leads' };
        const criteria = {};
        for (const [key, title] of Object.entries(routes)) {
          const link = card(title).getByRole('link', { name: 'View All', exact: true });
          await link.waitFor();
          criteria[key] = title + ' View All';
        }
        const decider = await ctx.jev();
        const answer = (await decider.ask('Open the complete list of leads from the dashboard.', {
          card: choice('Choose the dashboard card action that opens the complete list of leads.', criteria),
        })).card;
        if (answer.confidence < (Number(process.env.UAT_MIN_CONFIDENCE) || 0.9)) {
          throw inconclusive('Jev confidence was below the action threshold');
        }
        assert.equal(answer.choice, 'leads');
        await card(routes[answer.choice]).getByRole('link', { name: 'View All', exact: true }).click();
        await page.waitForURL(url => url.pathname === '/leads');
        return `${decider.model} selected Recent Leads at ${answer.confidence.toFixed(2)} confidence`;
      });
    }
  },
};
