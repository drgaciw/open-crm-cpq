import assert from 'node:assert/strict';
import { mkdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { goto } from '../../lib/browser.mjs';
import { blocked, runCheck, waitUntil } from '../../lib/health.mjs';
import { blockedReason } from '../../lib/preflight.mjs';
import { draftWithLine } from '../workflows/quote-lifecycle.mjs';

export default {
  id: 'released-regressions',

  async run(ctx) {
    const { page, api } = ctx;

    await runCheck(ctx, 'route.dashboard.mobile-overflow', async () => {
      const initialSize = page.viewportSize();
      try {
        await page.setViewportSize({ width: 390, height: 844 });
        await goto(page, '/dashboard');
        await page.getByText('Recent Leads', { exact: true }).waitFor();
        const layout = await page.locator('mat-sidenav-content').evaluate(content => ({
          width: content.clientWidth,
          scrollWidth: content.scrollWidth,
        }));
        assert(layout.scrollWidth <= layout.width, JSON.stringify(layout));
        return 'Dashboard stays within the 390px viewport';
      } finally {
        if (initialSize) await page.setViewportSize(initialSize);
      }
    });

    await runCheck(ctx, 'route.shell.skip-link-reproduction', async () => {
      await goto(page, '/contacts');
      const route = new URL(page.url()).pathname;
      await page.keyboard.press('Tab');
      const skip = page.getByRole('link', { name: 'Skip to main content' });
      assert(await skip.evaluate(el => el.ownerDocument.activeElement === el), 'Skip link is not the first focus target');
      await page.keyboard.press('Enter');
      await page.waitForFunction(() => document.activeElement?.id === 'main-content' || location.pathname !== '/contacts');
      assert.equal(new URL(page.url()).pathname, route, 'Skip link navigated away from Contacts');
      assert(await page.locator('#main-content').evaluate(el => el.ownerDocument.activeElement === el), 'Main content did not receive focus');
      return 'Keyboard activation retained Contacts and focused main content';
    });

    await runCheck(ctx, 'quotes.create.deal-type-read-only', async () => {
      await goto(page, '/quotes/new');
      const field = page.getByRole('textbox', { name: 'Deal type', exact: true });
      await field.waitFor();
      assert.equal(await field.inputValue(), 'New');
      assert(await field.evaluate(input => input.readOnly), 'Standard create must not bypass continuation workflows');
      return 'Standard quote create displays the New deal type';
    });

    await runCheck(ctx, 'quotes.documents.all-types-download', async () => {
      const reason = blockedReason(ctx.preflight, 'quote');
      if (reason) throw blocked(reason);
      const { quote } = await draftWithLine(ctx);
      await goto(page, '/quotes/' + quote.id);
      await page.getByRole('tab', { name: 'Documents', exact: true }).click();
      await mkdir(ctx.evidenceDir, { recursive: true });
      const artifacts = [];
      for (const type of ['Proposal', 'Order form', 'Statement of work']) {
        await page.getByLabel('Document type').click();
        await page.getByRole('option', { name: type, exact: true }).click();
        await page.getByRole('button', { name: 'Generate', exact: true }).click();
        const row = page.getByRole('row').filter({ has: page.getByRole('cell', { name: type, exact: true }) }).first();
        const download = page.waitForEvent('download');
        await row.getByRole('button', { name: 'Download', exact: true }).click();
        const file = join(ctx.evidenceDir, ctx.runId + '-' + type.toLowerCase().replaceAll(' ', '-') + '.pdf');
        await (await download).saveAs(file);
        assert.equal((await readFile(file)).subarray(0, 5).toString(), '%PDF-');
        artifacts.push(type);
      }
      return 'Generated and downloaded PDF bytes for ' + artifacts.join(', ');
    });

    await runCheck(ctx, 'quote-lines.remove-bulk', async () => {
      for (const gateway of ['quote', 'pricing', 'product', 'pricebook']) {
        const reason = blockedReason(ctx.preflight, gateway);
        if (reason) throw blocked(reason);
      }
      const { quote, product } = await draftWithLine(ctx);
      const path = '/api/v1/quotes/' + quote.id;
      for (const suffix of ['B', 'C']) {
        await api.post(path + '/line-items', {
          productExternalId: String(product.id),
          productName: product.name + ' ' + suffix,
          quantity: 1,
          listPrice: 25,
        });
      }
      await goto(page, '/quotes/' + quote.id);
      await page.getByRole('checkbox', { name: 'Select all line items' }).check();
      const actions = page.getByTestId('quote-lines-bulk-actions');
      await actions.getByText('3 selected', { exact: true }).waitFor();

      const mutations = [];
      const record = response => {
        if (new URL(response.url()).pathname.startsWith(path + '/line-items/')) {
          mutations.push([response.request().method(), response.status()]);
        }
      };
      page.on('response', record);
      try {
        await actions.getByRole('button', { name: 'Remove selected' }).click();
        await waitUntil(async () => (await api.get(path)).lineItems.length, count => count === 0, {
          timeoutMs: 5000,
          describe: count => 'Bulk removal left ' + count + ' lines. Mutation responses: ' + JSON.stringify(mutations),
        });
        assert.deepEqual(mutations, [['POST', 204]], 'Bulk remove must perform one server mutation');
        await page.reload();
        await page.getByText('No line items yet.', { exact: true }).waitFor();
      } finally {
        page.off('response', record);
      }
      return 'One POST removed all three quote lines and reload confirmed the persisted state';
    });
  },
};
