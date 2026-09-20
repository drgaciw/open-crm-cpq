/**
 * Kanban board (`/deals/kanban`). There is NO drag-and-drop: a card changes column only through
 * its "Deal stage actions" menu (Advance Stage / Mark Won / Mark Lost) or the detail page.
 *
 * The menu trigger's aria-label is identical on every card, so every lookup is scoped to the card
 * of a run-unique fixture deal. The board loads the newest 200 deals; fixtures are always newest.
 *
 * Check ids: `deals.kanban.<aspect>`
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, runSteps, waitUntil } from '../../lib/health.mjs';
import { withNativeDialog } from '../../lib/dialogs.mjs';
import { settleOverlays, expectNoSnackbar } from '../../lib/overlays.mjs';
import { scopeLocator, controlLocator } from '../../lib/fields.mjs';
import { ensure } from '../fixtures.mjs';
import { STAGES, stageLabel } from '../specs/deals.mjs';
import { expectApiStage } from './deal-lifecycle.mjs';

const API = '/api/v1/deals';
const TRIGGER = 'Deal stage actions';
const exact = (text) => new RegExp(`^\\s*${text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`);

/** Run-unique names end in a counter, so "…-lead-3" must not also match "…-lead-30". */
const named = (name) => new RegExp(`${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?!\\d)`);

async function openBoard(page) {
  await goto(page, '/deals/kanban');
  await page.locator('.kanban-column').first().waitFor({ state: 'visible' });
}

const column = (page, stage) =>
  page.locator('.kanban-column').filter({ has: page.locator('.column-title', { hasText: exact(stageLabel(stage)) }) });

const card = (page, deal) => page.locator('mat-card.deal-card').filter({ hasText: named(deal.name) }).first();

/** Wait until the fixture's card sits in the column of `stage`. */
async function expectCardIn(page, deal, stage) {
  await column(page, stage).locator('mat-card.deal-card').filter({ hasText: named(deal.name) }).first()
    .waitFor({ state: 'visible', timeout: 10000 });
}

/** Open a card's menu and return its item labels (icon ligature text stripped). */
async function openCardMenu(page, deal) {
  await card(page, deal).getByRole('button', { name: TRIGGER, exact: true }).click();
  await page.getByRole('menuitem').first().waitFor({ state: 'visible' });
  return (await page.getByRole('menuitem').allInnerTexts()).map((t) => t.replace(/^[a-z_]+\s+/, '').replace(/\s+/g, ' ').trim());
}

const closeMenu = async (page) => {
  await page.keyboard.press('Escape');
  await settleOverlays(page);
};

/**
 * @param {object} ctx
 * @param {{kanban: object, detail: object}} specs
 */
export async function runKanban(ctx, { kanban, detail }) {
  const { page, api } = ctx;
  const cardActions = (stage) => kanban.actions.filter((a) => a.in === 'card' && a.visibleWhen.includes(stage)).map((a) => a.name);
  const loseForm = detail.forms.find((f) => f.id === 'lose-reason');

  await runCheck(ctx, 'deals.kanban.columns', async () => {
    await openBoard(page);
    const titles = (await page.locator('.kanban-column .column-title').allInnerTexts()).map((t) => t.trim());
    const expected = STAGES.map((s) => s.label);
    if (JSON.stringify(titles) !== JSON.stringify(expected)) throw new Error(`columns are [${titles}], expected [${expected}]`);
    return titles.join(' | ');
  });

  await runCheck(ctx, 'deals.kanban.back-button', async () => {
    await openBoard(page);
    await page.getByRole('button', { name: 'Back to deals list', exact: true }).click();
    await page.waitForURL((url) => url.pathname === '/deals');
  });

  await runCheck(ctx, 'deals.kanban.no-drag-and-drop', async () => {
    await openBoard(page);
    const draggable = await page.locator('[cdkdrag], [cdkDrag], .cdk-drag, [draggable="true"]').count();
    if (draggable) throw new Error(`${draggable} draggable element(s) found — the spec says the board has no drag-and-drop; update the suite`);
    return 'no cdkDrag / draggable elements (H-CRM-029 gap still holds)';
  });

  // Counts: the badge of each column equals its rendered cards and what the API reports.
  const counted = await ensure(ctx, 'deal', { amount: 7300 });
  await runCheck(ctx, 'deals.kanban.column-counts-match-cards-and-api', async () => {
    await openBoard(page);
    await expectCardIn(page, counted, 'PROSPECTING');
    const all = await api.get(`${API}?page=0&size=200&sort=createdAt,desc`);
    const problems = [];
    const seen = [];
    for (const stage of STAGES) {
      const col = column(page, stage.value);
      const badge = Number((await col.locator('.column-count').innerText()).trim());
      const cards = await col.locator('mat-card.deal-card').count();
      const fromApi = all.filter((d) => d.stage === stage.value).length;
      seen.push(`${stage.label} ${badge}`);
      if (badge !== cards) problems.push(`${stage.label}: badge ${badge} but ${cards} card(s)`);
      if (badge !== fromApi) problems.push(`${stage.label}: badge ${badge} but the API lists ${fromApi}`);
      if (!cards && !(await col.getByText('No deals', { exact: true }).isVisible())) problems.push(`${stage.label}: empty column lacks "No deals"`);
    }
    if (problems.length) throw new Error(problems.join('; '));
    return seen.join(' · ');
  });

  await runCheck(ctx, 'deals.kanban.pipeline-totals', async () => {
    await openBoard(page);
    const summary = await api.get(`${API}/pipeline`);
    const open = summary.filter((s) => s.stage !== 'WON' && s.stage !== 'LOST');
    const count = open.reduce((n, s) => n + s.count, 0);
    const chip = page.locator('[aria-label="Pipeline summary"] .total-chip').first();
    await waitUntil(async () => (await chip.innerText().catch(() => '')).trim(), (text) => text === `${count} open`, {
      timeoutMs: 6000, describe: (text) => `totals chip says "${text}", the pipeline API sums to "${count} open"`,
    });
    return `${count} open`;
  });

  await runCheck(ctx, 'deals.kanban.card-shows-amount-and-opens-detail', async () => {
    await openBoard(page);
    const text = (await card(page, counted).innerText()).replace(/\s+/g, ' ');
    if (!text.includes('$7,300')) throw new Error(`card does not show the amount $7,300: ${text}`);
    await card(page, counted).locator('.card-title').click();
    await page.waitForURL((url) => url.pathname === `/deals/${counted.id}`);
  });

  // ── the card menu, one fixture walked through the board ───────────────────────
  const deal = await ensure(ctx, 'deal');
  await runSteps(ctx, [
    {
      id: 'deals.kanban.menu.PROSPECTING-items',
      run: async () => {
        await openBoard(page);
        await expectCardIn(page, deal, 'PROSPECTING');
        const items = await openCardMenu(page, deal);
        const path = new URL(page.url()).pathname;
        await closeMenu(page);
        if (path !== '/deals/kanban') throw new Error(`opening the card menu navigated to ${path}`);
        const expected = cardActions('PROSPECTING');
        if (JSON.stringify(items) !== JSON.stringify(expected)) throw new Error(`menu is [${items}], expected [${expected}]`);
        return items.join(' | ');
      },
    },
    {
      id: 'deals.kanban.menu.advance-moves-card',
      run: async () => {
        await openBoard(page);
        await openCardMenu(page, deal);
        await page.getByRole('menuitem', { name: 'Advance Stage' }).click();
        await expectApiStage(api, deal.id, 'QUALIFICATION');
        await expectCardIn(page, deal, 'QUALIFICATION');
        if (new URL(page.url()).pathname !== '/deals/kanban') throw new Error('advancing from the menu navigated away from the board');
        await expectNoSnackbar(page);
      },
    },
    {
      id: 'deals.kanban.menu.NEGOTIATION-has-no-advance',
      run: async () => {
        for (const _ of [1, 2, 3]) await api.post(`${API}/${deal.id}/advance`); // -> NEGOTIATION
        await openBoard(page);
        await expectCardIn(page, deal, 'NEGOTIATION');
        const items = await openCardMenu(page, deal);
        await closeMenu(page);
        const expected = cardActions('NEGOTIATION');
        if (JSON.stringify(items) !== JSON.stringify(expected)) throw new Error(`menu is [${items}], expected [${expected}]`);
        return items.join(' | ');
      },
    },
    {
      id: 'deals.kanban.menu.mark-won-cancel',
      run: async () => {
        await openBoard(page);
        await openCardMenu(page, deal);
        await withNativeDialog(page, { type: 'confirm', message: `Mark "${deal.name}" as Won?`, accept: false },
          () => page.getByRole('menuitem', { name: 'Mark Won' }).click());
        await page.waitForTimeout(400);
        const dto = await api.get(`${API}/${deal.id}`);
        if (dto.stage !== 'NEGOTIATION') throw new Error(`stage became ${dto.stage} although the confirm was cancelled`);
      },
    },
    {
      id: 'deals.kanban.menu.mark-won',
      run: async () => {
        await openBoard(page);
        await openCardMenu(page, deal);
        await withNativeDialog(page, { type: 'confirm', message: `Mark "${deal.name}" as Won?` },
          () => page.getByRole('menuitem', { name: 'Mark Won' }).click());
        await expectApiStage(api, deal.id, 'WON');
        await expectCardIn(page, deal, 'WON');
      },
    },
    {
      id: 'deals.kanban.closed-card-has-no-menu',
      run: async () => {
        await openBoard(page);
        await expectCardIn(page, deal, 'WON');
        if (await card(page, deal).getByRole('button', { name: TRIGGER, exact: true }).isVisible()) {
          throw new Error('a WON card still offers the stage-actions menu');
        }
      },
    },
  ]);

  await runCheck(ctx, 'deals.kanban.menu.mark-lost-dialog', async () => {
    const loser = await ensure(ctx, 'deal');
    await openBoard(page);
    await expectCardIn(page, loser, 'PROSPECTING');
    await openCardMenu(page, loser);
    await page.getByRole('menuitem', { name: 'Mark Lost' }).click();
    const dialog = scopeLocator(page, loseForm.scope);
    await dialog.waitFor({ state: 'visible' });
    if (new URL(page.url()).pathname !== '/deals/kanban') throw new Error('opening the lost dialog navigated away from the board');
    await controlLocator(dialog, loseForm.fields[0]).fill('Lost from the board (UAT)');
    await dialog.getByRole('button', { name: loseForm.submit.name, exact: true }).click();
    const dto = await expectApiStage(api, loser.id, 'LOST');
    if (dto.lostReason !== 'Lost from the board (UAT)') throw new Error(`lostReason is "${dto.lostReason}"`);
    await expectCardIn(page, loser, 'LOST');
  });
}
