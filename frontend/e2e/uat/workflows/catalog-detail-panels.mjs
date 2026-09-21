/**
 * Catalog detail panels: Option groups, Attribute values, Configuration rules.
 * Called from suites/catalog.mjs (only suites/ and hypotheses/ are auto-discovered).
 *
 * Every panel works on its own API-seeded product so a failure in one cannot starve another.
 * Child records (groups, rules, attribute definitions) are ledgered as they are created; the
 * ledger is torn down newest-first, so children go before their product.
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, runSteps, gap, waitUntil } from '../../lib/health.mjs';
import { confirmMatDialog } from '../../lib/dialogs.mjs';
import { expectNoSnackbar } from '../../lib/overlays.mjs';
import { scopeLocator, fillField, readField } from '../../lib/fields.mjs';
import { ensure, uniqueName } from '../fixtures.mjs';
import { optionGroupDialog, optionDialog, configRuleDialog } from '../specs/catalog.mjs';
import { runFieldContract } from '../runners/fieldContract.mjs';

const GROUPS = '/api/v1/product-options/groups';
const ATTRIBUTES = '/api/v1/product-attributes';
const RULES = '/api/v1/configuration/rules';

const section = (page, headingId) => page.locator(`section[aria-labelledby="${headingId}"]`);
const dialogOf = (page, title) => scopeLocator(page, { kind: 'dialog', title });
const byLabel = (form, label) => form.fields.find((f) => f.label === label);

async function optionGroups(ctx) {
  const { page, api, runId } = ctx;
  const product = await ensure(ctx, 'product');
  const path = `/catalog/${product.id}`;
  const spec = optionGroupDialog(path);
  const form = spec.forms[0];
  const groupName = uniqueName(runId, 'group');
  const state = { groupId: null };
  const panel = () => section(page, 'options-heading');
  const groupCard = () => scopeLocator(page, { kind: 'card', title: groupName });
  const apiGroups = () => api.get(`${GROUPS}/product/${product.id}`); // plain array, not a page

  await runCheck(ctx, 'catalog.options.empty-state', async () => {
    await goto(page, path);
    await panel().getByRole('heading', { name: 'Option groups', exact: true }).waitFor({ state: 'visible' });
    await panel().getByRole('heading', { name: 'No option groups', exact: true }).waitFor({ state: 'visible' });
    await panel().getByText('This product has no option groups yet.', { exact: true }).waitFor({ state: 'visible' });
  });

  await runFieldContract(ctx, spec, form);

  await runSteps(ctx, [
    {
      id: 'catalog.options.group.create',
      run: async () => {
        await goto(page, path);
        await panel().getByRole('button', { name: 'Create group', exact: true }).click();
        const dialog = dialogOf(page, 'New option group');
        await fillField(page, dialog, byLabel(form, 'Name'), groupName);
        await fillField(page, dialog, byLabel(form, 'Selection type'), 'REQUIRED_SINGLE');
        await fillField(page, dialog, byLabel(form, 'Min selections'), '1');
        await fillField(page, dialog, byLabel(form, 'Max selections'), '3');
        await dialog.getByRole('button', { name: 'Create', exact: true }).click();
        const closed = await dialog.waitFor({ state: 'hidden', timeout: 8000 }).then(() => true).catch(() => false);
        if (!closed) {
          const banner = await dialog.getByRole('alert').innerText().catch(() => '(no banner)');
          throw new Error(`dialog stayed open after Create — banner: "${banner.replace(/\s+/g, ' ').trim()}"`);
        }
        await groupCard().waitFor({ state: 'visible' });
        await expectNoSnackbar(page);
        const subtitle = (await groupCard().locator('mat-card-subtitle').innerText()).replace(/\s+/g, ' ').trim();
        if (subtitle !== 'REQUIRED_SINGLE · min 1 · max 3') throw new Error(`group subtitle is "${subtitle}"`);
        const created = (await apiGroups()).find((g) => g.name === groupName);
        if (!created) throw new Error('group is shown in the UI but the API does not list it');
        state.groupId = created.id;
        api.track('optionGroup', created.id, `${GROUPS}/${created.id}`);
        const wrong = [['selectionType', 'REQUIRED_SINGLE'], ['minSelections', 1], ['maxSelections', 3]]
          .filter(([key, want]) => created[key] !== want).map(([key, want]) => `${key}=${created[key]} (wanted ${want})`);
        if (wrong.length) throw new Error(`API holds ${wrong.join(', ')}`);
        await groupCard().getByText('No options in this group.', { exact: true }).waitFor({ state: 'visible' });
        return `group ${created.id}: ${subtitle}`;
      },
    },
    {
      id: 'catalog.options.option.add',
      run: async () => {
        await goto(page, path);
        await groupCard().getByRole('button', { name: 'Add option', exact: true }).click();
        const dialog = dialogOf(page, 'Add option');
        await dialog.getByText(`Group: ${groupName}`, { exact: true }).waitFor({ state: 'visible' });
        const optionForm = optionDialog(path).forms[0];
        await fillField(page, dialog, byLabel(optionForm, 'Name'), 'Gold tier');
        await fillField(page, dialog, byLabel(optionForm, 'Price adjustment'), '10');
        await fillField(page, dialog, byLabel(optionForm, 'Adjustment type'), 'PERCENTAGE');
        await fillField(page, dialog, byLabel(optionForm, 'Default option'), true);
        await dialog.getByRole('button', { name: 'Add', exact: true }).click();
        const closed = await dialog.waitFor({ state: 'hidden', timeout: 8000 }).then(() => true).catch(() => false);
        if (!closed) {
          const banner = await dialog.getByRole('alert').innerText().catch(() => '(no banner)');
          throw new Error(`dialog stayed open after Add — banner: "${banner.replace(/\s+/g, ' ').trim()}"`);
        }
        const item = groupCard().getByRole('listitem').filter({ hasText: 'Gold tier' });
        await item.waitFor({ state: 'visible' });
        const text = (await item.innerText()).replace(/\s+/g, ' ').trim();
        if (!text.includes('default') || !text.includes('10%')) throw new Error(`option row reads "${text}", expected the default badge and "10%"`);
        const group = (await apiGroups()).find((g) => g.id === state.groupId);
        const option = group?.options?.find((o) => o.name === 'Gold tier');
        if (!option) throw new Error('option is shown in the UI but missing from the API');
        if (Number(option.priceAdjustment) !== 10 || option.adjustmentType !== 'PERCENTAGE' || option.defaultOption !== true) {
          throw new Error(`API holds ${JSON.stringify({ p: option.priceAdjustment, t: option.adjustmentType, d: option.defaultOption })}`);
        }
        return text;
      },
    },
    {
      id: 'catalog.options.option.add-without-adjustment',
      run: async () => {
        await goto(page, path);
        await groupCard().getByRole('button', { name: 'Add option', exact: true }).click();
        const dialog = dialogOf(page, 'Add option');
        const optionForm = optionDialog(path).forms[0];
        await fillField(page, dialog, byLabel(optionForm, 'Name'), 'Plain tier');
        await fillField(page, dialog, byLabel(optionForm, 'Adjustment type'), 'ABSOLUTE');
        await fillField(page, dialog, byLabel(optionForm, 'Adjustment type'), '—'); // back to the null option
        const shown = await readField(dialog, byLabel(optionForm, 'Adjustment type'));
        await dialog.getByRole('button', { name: 'Add', exact: true }).click();
        await dialog.waitFor({ state: 'hidden', timeout: 8000 });
        await groupCard().getByRole('listitem').filter({ hasText: 'Plain tier' }).waitFor({ state: 'visible' });
        const group = (await apiGroups()).find((g) => g.id === state.groupId);
        const option = group?.options?.find((o) => o.name === 'Plain tier');
        if (!option) throw new Error('option missing from the API');
        if (option.adjustmentType != null && option.adjustmentType !== 'ABSOLUTE' && option.adjustmentType !== 'PERCENTAGE') {
          throw new Error(`unexpected adjustmentType ${option.adjustmentType}`);
        }
        return `"—" selected (trigger shows "${shown}"); API adjustmentType=${JSON.stringify(option.adjustmentType ?? null)}`;
      },
    },
    {
      id: 'catalog.options.group.delete-cancel-keeps-group',
      run: async () => {
        await goto(page, path);
        await groupCard().getByRole('button', { name: 'Delete group', exact: true }).click();
        await confirmMatDialog(page, {
          title: 'Delete option group',
          message: `Delete option group "${groupName}"? Options in the group will be removed.`,
          click: 'Cancel',
        });
        if (!(await apiGroups()).some((g) => g.id === state.groupId)) throw new Error('group disappeared although the confirm was cancelled');
      },
    },
    {
      id: 'catalog.options.group.delete',
      run: async () => {
        await goto(page, path);
        await groupCard().getByRole('button', { name: 'Delete group', exact: true }).click();
        await confirmMatDialog(page, { title: 'Delete option group', click: 'Delete' });
        await groupCard().waitFor({ state: 'hidden' });
        await expectNoSnackbar(page);
        if ((await apiGroups()).some((g) => g.id === state.groupId)) throw new Error('group is still listed by the API after delete');
        api.untrack('optionGroup', state.groupId);
        await panel().getByRole('heading', { name: 'No option groups', exact: true }).waitFor({ state: 'visible' });
      },
    },
  ]);

  // "Add option" field contract needs a group to exist; seed one through the API.
  const contractProduct = await ensure(ctx, 'product');
  const seeded = await api.post(GROUPS, {
    productId: contractProduct.id, name: uniqueName(runId, 'group'), selectionType: 'SINGLE', minSelections: 0, sortOrder: 0,
  });
  api.track('optionGroup', seeded.id, `${GROUPS}/${seeded.id}`);
  const optionSpec = optionDialog(`/catalog/${contractProduct.id}`);
  await runFieldContract(ctx, optionSpec, optionSpec.forms[0]);
}

async function attributeValues(ctx) {
  const { page, api, runId } = ctx;
  const product = await ensure(ctx, 'product');
  const path = `/catalog/${product.id}`;
  const panel = () => section(page, 'attrs-heading');
  const ATTRIBUTE = { label: 'Attribute', ariaLabel: 'Attribute definition', kind: 'select' };
  const VALUE = { label: 'Value', kind: 'input' };
  const setValue = () => panel().getByRole('button', { name: 'Set value', exact: true });

  // The UI offers only page 0 (size 50) of the definitions; a run-unique one is seeded here.
  const definitionName = `uat_${runId.toLowerCase()}_color`;
  const displayName = uniqueName(runId, 'attribute');
  const definition = await api.post(ATTRIBUTES, { name: definitionName, displayName, dataType: 'STRING', required: false, sortOrder: 0 });
  api.track('attributeDefinition', definition.id, `${ATTRIBUTES}/${definition.id}`);
  const optionLabel = `${displayName} (STRING)`;
  const values = () => api.get(`${ATTRIBUTES}/products/${product.id}/values`);

  await runCheck(ctx, 'catalog.attributes.empty-state', async () => {
    await goto(page, path);
    await panel().getByRole('heading', { name: 'Attribute values', exact: true }).waitFor({ state: 'visible' });
    await panel().getByRole('heading', { name: 'No attribute values', exact: true }).waitFor({ state: 'visible' });
    await panel().getByText('Set a value using the form below.', { exact: true }).waitFor({ state: 'visible' });
  });

  await runCheck(ctx, 'catalog.attributes.submit-gated-until-attribute-picked', async () => {
    await goto(page, path);
    await setValue().waitFor({ state: 'visible' });
    if (await setValue().isEnabled()) throw new Error('"Set value" is enabled before an attribute is picked');
    await fillField(page, panel(), VALUE, 'orphan value');
    if (await setValue().isEnabled()) throw new Error('"Set value" is enabled with a value but no attribute');
  });

  await runSteps(ctx, [
    {
      id: 'catalog.attributes.set-value',
      run: async () => {
        await goto(page, path);
        await fillField(page, panel(), ATTRIBUTE, optionLabel);
        await fillField(page, panel(), VALUE, 'crimson');
        await setValue().click();
        const row = panel().getByRole('row').filter({ hasText: 'crimson' });
        await row.waitFor({ state: 'visible' });
        await expectNoSnackbar(page);
        const text = (await row.innerText()).replace(/\s+/g, ' ').trim();
        if (!text.includes(definitionName) && !text.includes(displayName)) {
          throw new Error(`row reads "${text}" — neither the attribute name nor its display name is shown`);
        }
        await waitUntil(() => readField(panel(), VALUE), (v) => v === '', { describe: (v) => `Value input still holds "${v}" after a successful set` });
        const stored = (await values()).find((v) => v.attributeId === definition.id);
        if (stored?.value !== 'crimson') throw new Error(`API holds value ${JSON.stringify(stored?.value)}`);
        return text;
      },
    },
    {
      id: 'catalog.attributes.set-again-replaces-row',
      run: async () => {
        await goto(page, path);
        await panel().getByRole('row').filter({ hasText: 'crimson' }).waitFor({ state: 'visible' });
        await fillField(page, panel(), ATTRIBUTE, optionLabel);
        await fillField(page, panel(), VALUE, 'teal');
        await setValue().click();
        await panel().getByRole('row').filter({ hasText: 'teal' }).waitFor({ state: 'visible' });
        const bodyRows = await panel().locator('tbody tr').count();
        if (bodyRows !== 1) throw new Error(`${bodyRows} rows after setting the same attribute twice, expected 1`);
        const stored = (await values()).filter((v) => v.attributeId === definition.id);
        if (stored.length !== 1 || stored[0].value !== 'teal') throw new Error(`API holds ${JSON.stringify(stored.map((v) => v.value))}`);
      },
    },
  ]);
}

async function configRules(ctx) {
  const { page, api, runId } = ctx;
  const product = await ensure(ctx, 'product');
  const target = await ensure(ctx, 'product');
  const path = `/catalog/${product.id}`;
  const spec = configRuleDialog(path);
  const form = spec.forms[0];
  const ruleName = uniqueName(runId, 'rule');
  const state = { ruleId: null };
  const panel = () => section(page, 'config-rules-heading');
  const ruleRow = () => panel().getByRole('row').filter({ hasText: ruleName });
  const apiRules = () => api.get(`${RULES}/product/${product.id}`);

  await runCheck(ctx, 'catalog.rules.empty-state', async () => {
    await goto(page, path);
    await panel().getByRole('heading', { name: 'Configuration rules', exact: true }).waitFor({ state: 'visible' });
    await panel().getByRole('heading', { name: 'No configuration rules', exact: true }).waitFor({ state: 'visible' });
    await panel().getByText('Add REQUIRES / EXCLUDES / RECOMMENDS / DEFAULT / VALIDATES rules for this product.', { exact: true })
      .waitFor({ state: 'visible' });
  });

  await runFieldContract(ctx, spec, form);

  // H-CPQ-P-012 (gap): the DTO has errorMessage + status; the dialog offers neither.
  await runCheck(ctx, 'catalog.gap.config-rule-fields', async () => {
    await goto(page, path);
    await panel().getByRole('button', { name: 'Add rule', exact: true }).click();
    const dialog = dialogOf(page, 'New configuration rule');
    await dialog.waitFor({ state: 'visible' });
    const labels = (await dialog.locator('mat-label').allInnerTexts()).map((t) => t.trim());
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click();
    await dialog.waitFor({ state: 'hidden' });
    const missing = ['error message', 'status'].filter((want) => !labels.some((l) => l.toLowerCase().includes(want)));
    if (!missing.length) return `dialog now offers: ${labels.join(', ')}`;
    throw gap(`New configuration rule dialog has no ${missing.join(' / ')} field (fields: ${labels.join(', ')}); ConfigRuleCreateRequest.errorMessage can never be set from the UI`);
  }, { hypothesis: 'H-CPQ-P-012' });

  await runSteps(ctx, [
    {
      id: 'catalog.rules.create',
      run: async () => {
        await goto(page, path);
        await panel().getByRole('button', { name: 'Add rule', exact: true }).click();
        const dialog = dialogOf(page, 'New configuration rule');
        await fillField(page, dialog, byLabel(form, 'Name'), ruleName);
        await fillField(page, dialog, byLabel(form, 'Rule type'), 'EXCLUDES');
        await fillField(page, dialog, byLabel(form, 'Target product ID (optional)'), String(target.id));
        await fillField(page, dialog, byLabel(form, 'Condition expression (optional)'), 'quantity > 1');
        await fillField(page, dialog, byLabel(form, 'Description (optional)'), 'Created by the UAT.');
        await fillField(page, dialog, byLabel(form, 'Priority'), '5');
        await dialog.getByRole('button', { name: 'Create', exact: true }).click();
        const closed = await dialog.waitFor({ state: 'hidden', timeout: 8000 }).then(() => true).catch(() => false);
        if (!closed) {
          const banner = await dialog.getByRole('alert').innerText().catch(() => '(no banner)');
          throw new Error(`dialog stayed open after Create — banner: "${banner.replace(/\s+/g, ' ').trim()}"`);
        }
        await ruleRow().waitFor({ state: 'visible' });
        await expectNoSnackbar(page);
        const cells = (await ruleRow().getByRole('cell').allInnerTexts()).map((t) => t.replace(/\s+/g, ' ').trim());
        const expected = [ruleName, 'EXCLUDES', String(target.id), '5', 'quantity > 1'];
        if (JSON.stringify(cells.slice(0, 5)) !== JSON.stringify(expected)) throw new Error(`row reads [${cells.join(' | ')}], expected [${expected.join(' | ')}]`);
        const created = (await apiRules()).find((r) => r.name === ruleName);
        if (!created) throw new Error('rule is shown in the UI but the API does not list it');
        state.ruleId = created.id;
        api.track('configRule', created.id, `${RULES}/${created.id}`);
        const wrong = [['ruleType', 'EXCLUDES'], ['sourceProductId', product.id], ['targetProductId', target.id], ['priority', 5],
          ['conditionExpression', 'quantity > 1'], ['description', 'Created by the UAT.']]
          .filter(([key, want]) => created[key] !== want).map(([key, want]) => `${key}=${JSON.stringify(created[key])} (wanted ${JSON.stringify(want)})`);
        if (wrong.length) throw new Error(`API holds ${wrong.join(', ')}`);
        return cells.slice(0, 5).join(' | ');
      },
    },
    {
      id: 'catalog.rules.headers',
      run: async () => {
        await goto(page, path);
        await ruleRow().waitFor({ state: 'visible' });
        const headers = (await panel().getByRole('columnheader').allInnerTexts()).map((t) => t.trim());
        const expected = ['Name', 'Type', 'Target', 'Priority', 'Condition', 'Actions'];
        if (JSON.stringify(headers) !== JSON.stringify(expected)) throw new Error(`headers are [${headers.join(', ')}]`);
      },
    },
    {
      id: 'catalog.rules.delete-cancel-keeps-rule',
      run: async () => {
        await goto(page, path);
        await ruleRow().getByRole('button', { name: 'Delete', exact: true }).click();
        await confirmMatDialog(page, {
          title: 'Delete configuration rule',
          message: `Delete rule "${ruleName}"? This cannot be undone.`,
          click: 'Cancel',
        });
        if (!(await apiRules()).some((r) => r.id === state.ruleId)) throw new Error('rule disappeared although the confirm was cancelled');
      },
    },
    {
      id: 'catalog.rules.delete',
      run: async () => {
        await goto(page, path);
        await ruleRow().getByRole('button', { name: 'Delete', exact: true }).click();
        await confirmMatDialog(page, { title: 'Delete configuration rule', click: 'Delete' });
        await ruleRow().waitFor({ state: 'hidden' });
        await expectNoSnackbar(page);
        if ((await apiRules()).some((r) => r.id === state.ruleId)) throw new Error('rule is still listed by the API after delete');
        api.untrack('configRule', state.ruleId);
      },
    },
  ]);
}

/** @param {object} ctx Suite context. */
export async function runDetailPanels(ctx) {
  await optionGroups(ctx);
  await attributeValues(ctx);
  await configRules(ctx);
}
