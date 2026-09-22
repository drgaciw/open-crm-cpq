/**
 * CRUD runner: create through the UI -> values persisted (checked through the API, not the UI's
 * own optimistic state) -> detail page renders -> edit through the UI -> persisted -> delete ->
 * really gone. Steps are ordered; a failure marks the rest BLOCKED rather than cascading.
 *
 * Check ids: `<spec.id>.crud.<step>`
 */
import { goto } from '../../lib/browser.mjs';
import { runSteps } from '../../lib/health.mjs';
import { scopeLocator, fillField, normalizeValue, readPath } from '../../lib/fields.mjs';
import { withNativeDialog, confirmMatDialog } from '../../lib/dialogs.mjs';
import { ApiCallError } from '../../lib/api.mjs';
import { uniqueName } from '../fixtures.mjs';
import { openForm } from './fieldContract.mjs';

/** What the API should hold for a field, given what was typed into the UI. */
function expectedApiValue(field, uiValue) {
  if (field.kind === 'select') {
    const option = field.options?.find((o) => o.label === uiValue);
    return option ? option.value : uiValue;
  }
  if (field.kind === 'number') return Number(uiValue);
  return normalizeValue(uiValue, field.normalize);
}

function comparePersisted(form, values, dto, mode) {
  const problems = [];
  for (const field of form.fields) {
    const rule = field.persist?.[mode];
    if (!rule || !(field.label in values)) continue;
    const apiPath = field.persist.apiPath;
    const actual = readPath(dto, apiPath);
    const typed = expectedApiValue(field, values[field.label]);
    if (rule === 'ignored') {
      // Declared: the UI offers the field but the backend does not take it in this mode.
      if (String(actual) === String(typed)) continue;
      problems.push({ field, text: `${field.label}: UI sent "${typed}" but API holds "${actual}" (field is ignored on ${mode})` });
    } else if (String(actual ?? '') !== String(typed ?? '')) {
      problems.push({ field, text: `${field.label}: typed "${typed}", API holds "${actual}"` });
    }
  }
  return problems;
}

/**
 * @param {object} ctx
 * @param {import('../contract.mjs').PageSpec} spec  The list-page spec that owns the form.
 * @param {import('../contract.mjs').FormSpec} form
 * @param {object} options
 * @param {string} options.entity           Fixture/ledger type, e.g. 'organization'.
 * @param {string} options.apiPath          Collection path, e.g. '/api/v1/organizations'.
 * @param {string} options.nameField        Label of the field that receives the unique name.
 * @param {RegExp} options.detailUrl        Matches the detail URL; group 1 is the id.
 * @param {(id:string)=>string} options.editPath
 * @param {import('../contract.mjs').ActionSpec} [options.deleteAction]
 * @param {string} [options.listPath]       Where delete lands.
 */
export async function runCrud(ctx, spec, form, options) {
  const { page, api, runId } = ctx;
  const { entity, apiPath, nameField, detailUrl, editPath, deleteAction, listPath = spec.path } = options;
  const base = `${spec.id}.crud`;
  const name = uniqueName(runId, entity);
  const state = { id: null, values: {} };

  const fillAll = async (scope, pick) => {
    for (const field of form.fields) {
      const value = field.label === nameField ? pick.name : pick.value(field);
      if (value === undefined) continue;
      await fillField(page, scope, field, value);
      state.values[field.label] = value;
    }
  };

  await runSteps(ctx, [
    {
      id: `${base}.create`,
      run: async () => {
        const scope = await openForm(ctx, spec, form, { mode: 'create' });
        await fillAll(scope, { name, value: (f) => f.sample });
        await scope.getByRole('button', { name: form.modes?.create?.submit ?? form.submit.name, exact: true }).click();
        await page.waitForURL(detailUrl, { timeout: 15000 });
        state.id = page.url().match(detailUrl)[1];
        api.track(entity, state.id, `${apiPath}/${state.id}`);
        return `${name} -> id ${state.id}`;
      },
    },
    {
      id: `${base}.persist.create`,
      run: async () => {
        const dto = await api.get(`${apiPath}/${state.id}`);
        const problems = comparePersisted(form, state.values, dto, 'create');
        if (problems.length) throw new Error(problems.map((p) => p.text).join('; '));
        return `${Object.keys(state.values).length} fields verified through the API`;
      },
    },
    {
      id: `${base}.detail`,
      run: async () => {
        await goto(page, editPath(state.id).replace(/\/edit$/, ''));
        await page.getByRole('heading', { level: 1 }).filter({ hasText: name }).waitFor({ state: 'visible' });
      },
    },
    {
      id: `${base}.edit`,
      run: async () => {
        await goto(page, editPath(state.id));
        const scope = scopeLocator(page, form.scope);
        const heading = form.modes?.edit?.heading;
        if (heading) await page.getByRole('heading').filter({ hasText: heading }).first().waitFor({ state: 'visible' });
        const edited = form.fields.filter((f) => f.editSample !== undefined);
        if (!edited.length) return 'spec declares no editSample — nothing to change';
        for (const field of edited) {
          await fillField(page, scope, field, field.editSample);
          state.values[field.label] = field.editSample;
        }
        await scope.getByRole('button', { name: form.modes?.edit?.submit ?? 'Update', exact: true }).click();
        await page.waitForURL(detailUrl, { timeout: 15000 });
        return `changed ${edited.map((f) => f.label).join(', ')}`;
      },
    },
    {
      id: `${base}.persist.update`,
      run: async () => {
        const dto = await api.get(`${apiPath}/${state.id}`);
        const problems = comparePersisted(form, state.values, dto, 'update');
        if (problems.length) throw new Error(problems.map((p) => p.text).join('; '));
      },
    },
    ...(deleteAction
      ? [{
          id: `${base}.delete`,
          run: async () => {
            await goto(page, editPath(state.id).replace(/\/edit$/, ''));
            const button = page.getByRole('button', { name: deleteAction.name, exact: true }).first();
            const confirm = deleteAction.confirm;
            if (confirm?.kind === 'native') {
              await withNativeDialog(page, { type: 'confirm', message: confirm.message }, () => button.click());
            } else {
              await button.click();
              if (confirm?.kind === 'mat') {
                await confirmMatDialog(page, { title: confirm.title, message: confirm.message, click: confirm.confirmText ?? 'Delete' });
              }
            }
            await page.waitForURL((url) => url.pathname === listPath, { timeout: 15000 });
            // Proof of deletion comes from the API, not from the row merely vanishing.
            try {
              await api.get(`${apiPath}/${state.id}`);
            } catch (error) {
              if (error instanceof ApiCallError && error.status === 404) {
                api.untrack(entity, state.id);
                return `GET ${apiPath}/${state.id} -> 404`;
              }
              throw error;
            }
            throw new Error(`record ${state.id} is still readable through the API after delete`);
          },
        }]
      : []),
  ]);

  return state;
}
