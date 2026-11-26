import assert from 'node:assert/strict';
import { goto } from '../../lib/browser.mjs';
import { runCheck } from '../../lib/health.mjs';
import { ensure, uniqueName } from '../fixtures.mjs';

const apiPath = '/api/v1/product-bundles';

export default {
  id: 'product-bundles',
  backend: 'product',
  async run(ctx) {
    const { page, api } = ctx;
    const dialog = page.getByRole('dialog');

    await runCheck(ctx, 'product-bundles.metadata-and-items-persist', async () => {
      const parent = await ensure(ctx, 'product', { bundle: true });
      const child = await ensure(ctx, 'product');
      const name = uniqueName(ctx.runId, 'bundle');
      const editedName = name + ' edited';

      await goto(page, '/product-bundles');
      await page.locator('app-page-header').getByRole('button', { name: /New Bundle/i }).click();
      assert(await dialog.getByRole('button', { name: 'Create', exact: true }).isDisabled());
      await dialog.getByLabel('Product ID', { exact: true }).fill(String(parent.id));
      await dialog.getByLabel('Name', { exact: true }).fill(name);
      const created = page.waitForResponse(r =>
        new URL(r.url()).pathname === apiPath && r.request().method() === 'POST');
      await dialog.getByRole('button', { name: 'Create', exact: true }).click();
      const response = await created;
      assert.equal(response.status(), 201, 'create bundle HTTP ' + response.status());
      const { data: bundle } = await response.json();
      const path = apiPath + '/' + bundle.id;
      api.track('productBundle', bundle.id, path);
      await page.getByRole('heading', { name, exact: true }).waitFor();

      await page.getByRole('button', { name: /Edit bundle/ }).click();
      await dialog.getByLabel('Name', { exact: true }).fill(editedName);
      await dialog.getByRole('button', { name: 'Save', exact: true }).click();
      await page.getByRole('heading', { name: editedName, exact: true }).waitFor();
      assert.equal((await api.get(path)).name, editedName);

      await page.getByRole('button', { name: /Add item/ }).click();
      await dialog.getByLabel('Product ID', { exact: true }).fill(String(child.id));
      await dialog.getByRole('button', { name: 'Add', exact: true }).click();
      const row = page.getByRole('row').filter({ has: page.getByRole('cell', { name: String(child.id), exact: true }) });
      await row.getByRole('button', { name: 'Edit', exact: true }).click();
      await dialog.getByLabel('Quantity', { exact: true }).fill('3');
      await dialog.getByLabel('Sort order', { exact: true }).fill('2');
      await dialog.getByRole('checkbox', { name: 'Optional item' }).check();
      await dialog.getByRole('button', { name: 'Save', exact: true }).click();
      await row.getByRole('cell', { name: '3', exact: true }).waitFor();
      await page.reload();
      await row.getByRole('cell', { name: '3', exact: true }).waitFor();
      const persisted = await api.get(path);
      assert.equal(persisted.name, editedName);
      assert.equal(persisted.productId, parent.id);
      assert.equal(persisted.items[0].quantity, 3);
      assert.equal(persisted.items[0].optional, true);
      assert.equal(persisted.items[0].sortOrder, 2);

      const tree = page.getByRole('tree', { name: 'Bundle hierarchy' });
      const toggle = tree.getByRole('button', { name: 'Toggle ' + editedName });
      await toggle.click();
      const children = tree.getByRole('group').first();
      await children.waitFor();
      await toggle.click();
      await children.waitFor({ state: 'hidden' });

      await row.getByRole('button', { name: 'Remove', exact: true }).click();
      await dialog.getByRole('button', { name: 'Cancel', exact: true }).click();
      await dialog.waitFor({ state: 'hidden' });
      await row.waitFor();
      await row.getByRole('button', { name: 'Remove', exact: true }).click();
      await dialog.getByRole('button', { name: 'Remove', exact: true }).click();
      await page.getByText('No items', { exact: true }).waitFor();
      assert.equal((await api.get(path)).items.length, 0);
      await page.getByRole('button', { name: 'Delete', exact: true }).click();
      await dialog.getByRole('button', { name: 'Delete', exact: true }).click();
      await page.waitForURL(url => url.pathname === '/product-bundles');
      await assert.rejects(api.get(path), { status: 404 });
      api.untrack('productBundle', bundle.id);
      return 'bundle creation, metadata and item edits, reload, tree toggle, item removal, and bundle deletion';
    });
  },
};
