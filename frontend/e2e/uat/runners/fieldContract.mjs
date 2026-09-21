/**
 * Field-contract runner: every field a form spec declares is reachable by role + label, has the
 * declared default and option list, and every validator behaves as declared — the exact
 * mat-error text appears (or provably does not) and the submit button is gated.
 *
 * Check ids: `<spec.id>.form.<form.id>.<aspect>` and `<spec.id>.field.<label>.<aspect>`
 */
import { goto } from '../../lib/browser.mjs';
import { runCheck, defect } from '../../lib/health.mjs';
import { scopeLocator, controlLocator, fillField, blurField, readField, fieldError, selectOptions } from '../../lib/fields.mjs';
import { closeStrayOverlays } from '../../lib/overlays.mjs';

/**
 * Bring the form on screen. Page forms navigate; dialog forms click their opening action.
 * Always starts from a fresh load so one validator's junk input cannot leak into the next check.
 */
export async function openForm(ctx, spec, form, { mode = 'create' } = {}) {
  const { page } = ctx;
  await closeStrayOverlays(page).catch(() => {});
  const path = form.modes?.[mode]?.path ?? form.open?.path;
  if (path) {
    await goto(page, path);
  } else {
    await goto(page, spec.path);
    await page.getByRole('button', { name: form.open.action, exact: true }).first().click();
  }
  const scope = scopeLocator(page, form.scope);
  await scope.waitFor({ state: 'visible' });
  return scope;
}

const submitButton = (scope, form, mode = 'create') =>
  scope.getByRole('button', { name: form.modes?.[mode]?.submit ?? form.submit?.name, exact: true }).first();

/**
 * @param {object} ctx
 * @param {import('../contract.mjs').PageSpec} spec
 * @param {import('../contract.mjs').FormSpec} form
 */
export async function runFieldContract(ctx, spec, form) {
  const { page } = ctx;
  const base = `${spec.id}.form.${form.id}`;

  const opened = await runCheck(ctx, `${base}.opens`, async () => {
    const scope = await openForm(ctx, spec, form);
    const heading = form.modes?.create?.heading;
    if (heading) {
      const text = (await scope.page().getByRole('heading').filter({ hasText: heading }).first().innerText()).trim();
      return `heading "${text}"`;
    }
  });
  if (!opened) return;

  const requiredFields = form.fields.filter((f) => f.required);
  if (requiredFields.length && form.submit) {
    await runCheck(ctx, `${base}.submit.gated-when-empty`, async () => {
      const scope = await openForm(ctx, spec, form);
      if (await submitButton(scope, form).isEnabled()) {
        throw new Error(`"${form.submit.name}" is enabled on an empty form with required fields: ${requiredFields.map((f) => f.label).join(', ')}`);
      }
    });
  }

  for (const field of form.fields) {
    const fid = `${spec.id}.field.${field.label}`;

    const present = await runCheck(ctx, `${fid}.present`, async () => {
      const scope = await openForm(ctx, spec, form);
      await controlLocator(scope, field).waitFor({ state: 'visible', timeout: 6000 });
      if (field.placeholder) {
        const actual = await controlLocator(scope, field).getAttribute('placeholder');
        if (actual !== field.placeholder) throw new Error(`placeholder is "${actual}", expected "${field.placeholder}"`);
      }
    }, { hypothesis: field.hypothesis });
    if (!present) continue;

    if (field.default !== undefined) {
      await runCheck(ctx, `${fid}.default`, async () => {
        const scope = await openForm(ctx, spec, form);
        const actual = await readField(scope, field);
        if (String(actual) !== String(field.default)) throw new Error(`default is "${actual}", expected "${field.default}"`);
      });
    }

    if (field.kind === 'select' && field.options) {
      await runCheck(ctx, `${fid}.options`, async () => {
        const scope = await openForm(ctx, spec, form);
        const actual = await selectOptions(page, scope, field);
        const expected = field.options.map((o) => o.label);
        if (JSON.stringify(actual) !== JSON.stringify(expected)) {
          throw new Error(`options differ — expected [${expected.join(' | ')}], saw [${actual.join(' | ')}]`);
        }
        return `${actual.length} options`;
      });
    }

    for (const validator of field.validators ?? []) {
      await runCheck(ctx, `${fid}.validator.${validator.kind}`, async () => {
        const scope = await openForm(ctx, spec, form);

        // Satisfy every *other* required field so the submit state isolates this validator.
        for (const other of form.fields) {
          if (other !== field && other.required && other.sample !== undefined) {
            await fillField(page, scope, other, other.sample);
          }
        }
        if (validator.sample !== '') await fillField(page, scope, field, validator.sample);
        await blurField(page, scope, field);

        const shown = await fieldError(scope, field);
        const wantsError = validator.expectError;
        if (wantsError === null) {
          if (shown) return `an error is now shown: "${shown}" — hypothesis no longer holds`;
          // Declared defect: the validator blocks submit but tells the user nothing.
          const gated = form.submit ? !(await submitButton(scope, form).isEnabled()) : false;
          throw defect(`"${validator.sample}" is rejected${gated ? ' (submit disabled)' : ''} but no mat-error is shown`);
        }
        const matches = wantsError instanceof RegExp ? wantsError.test(shown ?? '') : shown === wantsError;
        if (!matches) throw new Error(`mat-error is ${shown === null ? 'absent' : `"${shown}"`}, expected "${wantsError}"`);

        if (validator.gatesSubmit !== false && form.submit && (await submitButton(scope, form).isEnabled())) {
          throw new Error(`"${form.submit.name}" stays enabled while "${field.label}" is invalid`);
        }
        return `"${shown}"`;
      }, { hypothesis: validator.hypothesis });
    }
  }
}
