import assert from 'node:assert/strict';
import { goto } from '../../lib/browser.mjs';
import { runCheck } from '../../lib/health.mjs';
import { ensure, uniqueName } from '../fixtures.mjs';

function responseFor(page, path, { method = 'GET', query } = {}) {
  return page.waitForResponse(response => {
    const url = new URL(response.url());
    return url.pathname === path && response.request().method() === method
      && (!query || url.searchParams.has(query));
  });
}

export default {
  id: 'pricing-controls',
  backend: 'pricing',
  async run(ctx) {
    const { page, api } = ctx;
    const dialog = page.getByRole('dialog');

    for (const [route, filter, all] of [
      ['pricing-customers', 'segment', 'All segments'],
      ['rule-sets', 'status', 'All statuses'],
    ]) {
      await runCheck(ctx, `pricing-controls.${route}.filter-and-clear`, async () => {
        const path = '/api/v1/' + route;
        const initial = responseFor(page, path);
        await goto(page, '/' + route);
        assert.equal((await initial).status(), 200);
        const selector = page.getByRole('combobox', { name: 'Filter by ' + filter });
        await selector.click();
        const filtered = responseFor(page, path, { query: filter });
        await page.getByRole('option').filter({ hasNotText: all }).first().click();
        assert.equal((await filtered).status(), 200);
        const reset = responseFor(page, path);
        await page.getByRole('button', { name: 'Clear', exact: true }).click();
        const response = await reset;
        assert.equal(response.status(), 200);
        assert.equal(new URL(response.url()).searchParams.has(filter), false);
        return filter + ' selected and cleared through the API';
      });
    }

    await runCheck(ctx, 'pricing-controls.customer-create-edit-delete', async () => {
      const name = uniqueName(ctx.runId, 'pricing-customer');
      const path = '/api/v1/pricing-customers';
      await goto(page, '/pricing-customers');
      await page.locator('app-page-header').getByRole('button', { name: /New pricing customer/ }).click();
      const create = dialog.getByRole('button', { name: 'Create', exact: true });
      assert(await create.isDisabled());
      await dialog.getByLabel('External ID', { exact: true }).fill(name);
      await dialog.getByLabel('Name', { exact: true }).fill(name);
      const created = responseFor(page, path, { method: 'POST' });
      await create.click();
      const response = await created;
      assert.equal(response.status(), 201, 'customer create HTTP ' + response.status());
      const { data: customer } = await response.json();
      const detail = path + '/' + customer.id;
      api.track('pricingCustomer', customer.id, detail);
      const heading = page.getByRole('heading', { name, exact: true });
      await heading.waitFor();
      await page.getByRole('button', { name: 'Edit', exact: true }).click();
      await page.getByLabel('Name', { exact: true }).fill(name + ' discarded');
      await page.getByRole('button', { name: 'Cancel', exact: true }).click();
      assert.equal((await api.get(detail)).name, name);
      await page.getByRole('button', { name: 'Edit', exact: true }).click();
      await page.getByLabel('Name', { exact: true }).fill(name + ' saved');
      await page.getByRole('button', { name: 'Save', exact: true }).click();
      await page.getByRole('heading', { name: name + ' saved', exact: true }).waitFor();
      assert.equal((await api.get(detail)).name, name + ' saved');
      await page.getByRole('button', { name: 'Delete', exact: true }).click();
      await dialog.getByRole('button', { name: 'Delete', exact: true }).click();
      await page.waitForURL(url => url.pathname === '/pricing-customers');
      await assert.rejects(api.get(detail), { status: 404 });
      api.untrack('pricingCustomer', customer.id);
      return 'created, cancelled an edit, saved and verified a second edit, and deleted';
    });

    await runCheck(ctx, 'pricing-controls.draft-rule-create-edit-delete', async () => {
      const name = uniqueName(ctx.runId, 'rule-set');
      await goto(page, '/rule-sets');
      await page.locator('app-page-header').getByRole('button', { name: /New rule set/ }).click();
      const create = dialog.getByRole('button', { name: 'Create', exact: true });
      assert(await create.isDisabled());
      await dialog.getByLabel('Name', { exact: true }).fill(name);
      const created = responseFor(page, '/api/v1/rule-sets', { method: 'POST' });
      await create.click();
      const response = await created;
      assert.equal(response.status(), 201, 'rule-set create HTTP ' + response.status());
      const { data: ruleSet } = await response.json();
      api.track('ruleSet', ruleSet.id, '/api/v1/rule-sets/' + ruleSet.id);
      await page.getByRole('heading', { name, exact: true }).waitFor();

      const rule = uniqueName(ctx.runId, 'rule');
      await page.getByRole('button', { name: /Add rule/ }).click();
      await dialog.getByLabel('Name', { exact: true }).fill(rule);
      await dialog.getByLabel('DRL content').fill('rule "UAT no-op"\nwhen\nthen\nend');
      const added = responseFor(page, '/api/v1/rules', { method: 'POST' });
      await create.click();
      const ruleResponse = await added;
      assert.equal(ruleResponse.status(), 201, 'rule create HTTP ' + ruleResponse.status());
      const { data: createdRule } = await ruleResponse.json();
      api.track('rule', createdRule.id, '/api/v1/rules/' + createdRule.id);
      await dialog.waitFor({ state: 'hidden' });
      const ruleRow = page.getByRole('row').filter({ hasText: rule });
      await ruleRow.getByRole('button', { name: 'Edit', exact: true }).click();
      await dialog.getByLabel('Name', { exact: true }).fill(rule + ' edited');
      await dialog.getByRole('button', { name: 'Save', exact: true }).click();
      await dialog.waitFor({ state: 'hidden' });
      await page.reload();
      await ruleRow.getByText(rule + ' edited', { exact: true }).waitFor();
      const impact = responseFor(page, '/api/v1/rules/' + createdRule.id + '/impact');
      await ruleRow.getByRole('button', { name: 'Impact', exact: true }).click();
      assert.equal((await impact).status(), 200);
      await dialog.getByText('Pricing executions (last 30d)', { exact: true }).waitFor();
      await dialog.getByRole('button', { name: 'Close', exact: true }).click();
      await dialog.waitFor({ state: 'hidden' });
      await ruleRow.getByRole('button', { name: 'Delete', exact: true }).click();
      await dialog.getByRole('button', { name: 'Delete', exact: true }).click();
      await ruleRow.waitFor({ state: 'hidden' });
      api.untrack('rule', createdRule.id);
      return 'created a draft rule set, validated and edited its rule, checked reload and impact, and deleted the rule';
    });

    await runCheck(ctx, 'pricing-controls.simulator.single', async () => {
      await goto(page, '/pricing/simulator');
      const quantity = page.getByRole('spinbutton', { name: 'Quantity', exact: true });
      const simulate = page.getByRole('button', { name: 'Simulate', exact: true });
      await quantity.fill('1.5');
      assert.equal(await simulate.isDisabled(), true);
      await quantity.fill('2');
      await page.getByRole('spinbutton', { name: 'List price override' }).fill('25');
      const customer = await ensure(ctx, 'pricingCustomer');
      const regionalBooks = await ctx.api.get('/api/v1/price-books?region=NA');
      if (!regionalBooks.content.length) await ensure(ctx, 'priceBook');
      await page.getByRole('textbox', { name: 'Customer external ID' }).fill(customer.externalId);
      const executed = responseFor(page, '/api/v1/pricing/execute', { method: 'POST' });
      await simulate.click();
      const response = await executed;
      assert.equal(response.status(), 200, 'pricing execute HTTP ' + response.status());
      assert.equal(response.request().postDataJSON().lineItems[0].quantity, 2);
      await page.getByText(/Execute OK/).waitFor();
      await page.getByRole('heading', { name: 'Line results' }).waitFor();
      return 'fractional quantity rejected; valid request rendered result metrics and line items';
    });

    await runCheck(ctx, 'pricing-controls.simulator.bulk', async () => {
      await goto(page, '/pricing/simulator');
      await page.getByRole('tab', { name: 'Bulk', exact: true }).click();
      const input = page.getByTestId('bulk-json-input');
      const simulate = page.getByTestId('bulk-simulate');
      await input.fill('{');
      await simulate.click();
      await page.getByTestId('bulk-simulator').getByRole('alert').waitFor();
      await page.getByTestId('bulk-load-sample').click();
      await page.waitForFunction(() => document.querySelector('#bulk-json-input')?.value.startsWith('['));
      const contexts = JSON.parse(await input.inputValue());
      assert(contexts.length >= 2);
      const customer = await ensure(ctx, 'pricingCustomer');
      const book = await ensure(ctx, 'priceBook', { region: 'UAT-' + ctx.runId });
      contexts[0].customerExternalId = customer.externalId;
      contexts[0].region = book.region;
      contexts[0].lineItems[0].listPriceOverride = 25;
      contexts[1].customerExternalId = 'missing-' + ctx.runId;
      const contents = JSON.stringify({ contexts });
      await page.getByTestId('bulk-file-input').setInputFiles({
        name: 'pricing-contexts.json', mimeType: 'application/json', buffer: Buffer.from(contents),
      });
      await page.waitForFunction(value => document.querySelector('#bulk-json-input')?.value === value, contents);
      const simulated = responseFor(page, '/api/v1/pricing/simulate/bulk', { method: 'POST' });
      await simulate.click();
      const response = await simulated;
      assert.equal(response.status(), 200, 'bulk simulate HTTP ' + response.status());
      await page.getByTestId('bulk-summary').getByText('Total requests: ' + contexts.length).waitFor();
      await page.getByTestId('bulk-summary').getByText('Successful requests: 1', { exact: true }).waitFor();
      const download = page.waitForEvent('download');
      await page.getByTestId('bulk-download-results').click();
      assert.match((await download).suggestedFilename(), /\.json$/i);
      return 'invalid JSON rejected; sample, file upload, live results, and JSON download worked';
    });
  },
};
