/**
 * Unit tests for the UAT spec contract. Run with: npm run e2e:test
 * A spec typo must fail here at load time, not as a confusing browser error.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { validateSpec, validateHypothesis, OUTCOMES, SpecError } from './contract.mjs';

const minimal = () => ({ id: 'organizations', path: '/organizations', heading: 'Organizations' });

test('accepts a minimal page spec', () => {
  assert.equal(validateSpec(minimal()).id, 'organizations');
});

test('rejects an unknown top-level key and names it', () => {
  assert.throws(() => validateSpec({ ...minimal(), headng: 'typo' }), (e) => {
    assert.ok(e instanceof SpecError);
    assert.match(e.message, /headng/);
    return true;
  });
});

test('requires id, path and heading', () => {
  for (const key of ['id', 'path', 'heading']) {
    const spec = minimal();
    delete spec[key];
    assert.throws(() => validateSpec(spec), new RegExp(key));
  }
});

test('heading may be a RegExp for data-driven titles', () => {
  assert.ok(validateSpec({ ...minimal(), heading: /Approval/i }));
});

test('rejects an unknown field kind', () => {
  const spec = {
    ...minimal(),
    forms: [{ id: 'f', scope: { kind: 'page' }, fields: [{ label: 'Name', kind: 'inptu' }] }],
  };
  assert.throws(() => validateSpec(spec), /inptu/);
});

test('reports the path to a nested error', () => {
  const spec = {
    ...minimal(),
    forms: [{ id: 'f', scope: { kind: 'page' }, fields: [{ label: 'Name', kind: 'input', requird: true }] }],
  };
  assert.throws(() => validateSpec(spec), /forms\[0\]\.fields\[0\].*requird/);
});

test('a dialog scope must carry a title', () => {
  const spec = { ...minimal(), forms: [{ id: 'f', scope: { kind: 'dialog' }, fields: [] }] };
  assert.throws(() => validateSpec(spec), /title/);
});

test('select fields must declare label/value option pairs', () => {
  const bad = {
    ...minimal(),
    forms: [{ id: 'f', scope: { kind: 'page' }, fields: [{ label: 'Stage', kind: 'select', options: ['A'] }] }],
  };
  assert.throws(() => validateSpec(bad), /options\[0\]/);
  const good = structuredClone(bad);
  good.forms[0].fields[0].options = [{ label: 'Prospecting', value: 'PROSPECTING' }];
  assert.ok(validateSpec(good));
});

test('a validator with no visible error must name its hypothesis', () => {
  const field = (validator) => ({
    ...minimal(),
    forms: [{ id: 'f', scope: { kind: 'page' }, fields: [{ label: 'P', kind: 'number', validators: [validator] }] }],
  });
  // expectError:null means "submit disables but nothing is shown" — that is a defect claim,
  // so it has to be traceable to a registered hypothesis rather than silently tolerated.
  assert.throws(() => validateSpec(field({ kind: 'max', sample: '150', expectError: null })), /hypothesis/);
  assert.ok(validateSpec(field({ kind: 'max', sample: '150', expectError: null, hypothesis: 'H-CRM-016' })));
  assert.ok(validateSpec(field({ kind: 'required', sample: '', expectError: 'Required' })));
});

test('actions validate confirm kinds', () => {
  const spec = (kind) => ({
    ...minimal(),
    actions: [{ name: 'Delete', in: 'header', confirm: { kind, message: /Delete/ } }],
  });
  assert.ok(validateSpec(spec('native')));
  assert.ok(validateSpec(spec('mat')));
  assert.ok(validateSpec(spec('prompt')));
  assert.throws(() => validateSpec(spec('modal')), /modal/);
});

test('hypotheses need a known triage class, and a checkId only when browser-checkable', () => {
  const base = { id: 'H-CRM-003', claim: 'Stage ignored on create', file: 'deal-form.component.ts' };
  assert.ok(validateHypothesis({ ...base, triage: 'browserCheckable', checkId: 'deals.persist.stage' }));
  assert.throws(() => validateHypothesis({ ...base, triage: 'browserCheckable' }), /checkId/);
  assert.ok(validateHypothesis({ ...base, triage: 'gap' }));
  assert.throws(() => validateHypothesis({ ...base, triage: 'maybe' }), /maybe/);
});

test('exposes the closed set of outcomes', () => {
  assert.deepEqual(Object.keys(OUTCOMES).sort(), [
    'BLOCKED', 'EXPECTED_FAIL', 'FAIL', 'GAP', 'INCONCLUSIVE', 'INFO', 'PASS',
  ]);
  assert.ok(Object.isFrozen(OUTCOMES));
});
