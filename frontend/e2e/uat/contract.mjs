/**
 * The frozen contract between UAT specs (data) and UAT runners (behaviour).
 *
 * Specs describe a page declaratively; generic runners interpret them. `validateSpec`
 * rejects unknown keys so a typo in a spec fails at load time with a path, instead of
 * surfacing later as a confusing browser timeout.
 *
 * Change this file deliberately: every spec and runner depends on it.
 *
 * @typedef {{kind:'page'}|{kind:'dialog',title:string|RegExp}|{kind:'card',title:string|RegExp}
 *   |{kind:'row',rowText:string|RegExp,within?:Scope}|{kind:'css',selector:string}} Scope
 *
 * @typedef {object} ValidatorSpec
 * @property {'required'|'email'|'min'|'max'|'minLength'|'maxLength'|'pattern'} kind
 * @property {string} sample           Input that violates the validator ('' for required).
 * @property {string|RegExp|null} expectError  Exact mat-error text; null = none is rendered.
 * @property {boolean} [gatesSubmit]   Default true: the submit button must be disabled.
 * @property {string} [hypothesis]     Required when expectError is null.
 *
 * @typedef {object} FieldSpec
 * @property {string} label            mat-label text = accessible name.
 * @property {'input'|'number'|'textarea'|'select'|'autocomplete'|'date'|'checkbox'} kind
 * @property {string} [ariaLabel]      Overrides label for lookup when the control has one.
 * @property {boolean} [required]
 * @property {string} [placeholder]
 * @property {string|number|boolean} [default]
 * @property {string|number|boolean} [sample]      A valid value used by the CRUD runner.
 * @property {string|number|boolean} [editSample]  A different valid value for the edit pass.
 * @property {{label:string,value:string|null}[]} [options]
 * @property {'trim'|'upper'|'number'} [normalize]  Applied before comparing UI vs API.
 * @property {{create?:boolean|'ignored',update?:boolean,apiPath?:string}} [persist]
 * @property {ValidatorSpec[]} [validators]
 * @property {{entity:string,searchUrl:RegExp}} [source]  Autocomplete backing entity.
 * @property {boolean} [requiresPick]  Typed text alone is not persisted.
 * @property {number} [minChars]
 * @property {string} [inputFormat]    Date typing format, e.g. 'M/D/YYYY'.
 * @property {string} [apiFormat]
 * @property {string} [hypothesis]     Links a field-level claim to the registry.
 *
 * @typedef {object} FormSpec
 * @property {string} id
 * @property {Scope} scope
 * @property {'reactive'|'signal'} [flavor]
 * @property {{action?:string,path?:string}} [open]   How to reach the form.
 * @property {{name:string,enterSubmits?:boolean}} [submit]
 * @property {{name:string}} [cancel]
 * @property {'banner'|'snackbar'|'both'} [serverError]
 * @property {{create?:ModeSpec,edit?:ModeSpec}} [modes]
 * @property {FieldSpec[]} fields
 *
 * @typedef {{path?:string,heading?:string|RegExp,submit?:string}} ModeSpec
 *
 * @typedef {object} ActionSpec
 * @property {string} name
 * @property {'header'|'menu'|'row'|'card'} in
 * @property {string} [menuTrigger]    aria-label of the button that opens the menu.
 * @property {string} [ariaLabel]
 * @property {string} [css]
 * @property {string[]} [enabledWhen]  Statuses in which the action is enabled.
 * @property {string[]} [visibleWhen]  Statuses in which the action is rendered at all.
 * @property {string} [titleDisabled]  Tooltip shown while disabled.
 * @property {{kind:'mat'|'native'|'prompt',title?:string,message?:string|RegExp,
 *   confirmText?:string,cancelText?:string}} [confirm]
 * @property {{status?:string,navigate?:string|RegExp}} [effect]
 * @property {string} [navigatesTo]
 * @property {string} [opensDialog]
 * @property {string} [hypothesis]
 *
 * @typedef {object} TableSpec
 * @property {Scope} [scope]
 * @property {string[]} columns
 * @property {string[]} [sortable]
 * @property {number[]} [pageSizes]
 * @property {'row'|'tr'} [rowRole]
 *
 * @typedef {{title:string,labels?:string[],emptyText?:string,optional?:boolean}} CardSpec
 *
 * @typedef {object} PageSpec
 * @property {string} id
 * @property {string} path
 * @property {string|RegExp} heading
 * @property {string} [subtitle]
 * @property {string} [backend]        Preflight key; suite is BLOCKED when it is not 'up'.
 * @property {string} [sidebar]        Sidebar link label that leads here.
 * @property {string} [searchPlaceholder]
 * @property {ActionSpec[]} [actions]
 * @property {FormSpec[]} [forms]
 * @property {TableSpec[]} [tables]
 * @property {CardSpec[]} [cards]
 * @property {{title:string,message?:string,action?:string}} [emptyState]
 * @property {string[]} [expectedErrors]  Console/5xx patterns tolerated on this page.
 * @property {string} [notes]
 *
 * @typedef {object} Hypothesis
 * @property {string} id
 * @property {string} claim
 * @property {string} file
 * @property {'browserCheckable'|'unitOnly'|'gap'|'falsePositive'} triage
 * @property {string} [checkId]        Required when browserCheckable.
 * @property {'functional'|'a11y'|'consistency'|'cosmetic'} [class]
 * @property {string} [note]
 */

/** Every check ends in exactly one of these. */
export const OUTCOMES = Object.freeze({
  PASS: 'PASS',
  FAIL: 'FAIL',
  EXPECTED_FAIL: 'EXPECTED_FAIL',
  BLOCKED: 'BLOCKED',
  INCONCLUSIVE: 'INCONCLUSIVE',
  GAP: 'GAP',
  INFO: 'INFO',
});

export class SpecError extends Error {
  constructor(path, message) {
    super(`${path}: ${message}`);
    this.name = 'SpecError';
    this.path = path;
  }
}

const FIELD_KINDS = ['input', 'number', 'textarea', 'select', 'autocomplete', 'date', 'checkbox'];
const VALIDATOR_KINDS = ['required', 'email', 'min', 'max', 'minLength', 'maxLength', 'pattern'];
const SCOPE_KINDS = ['page', 'dialog', 'card', 'row', 'css'];
const CONFIRM_KINDS = ['mat', 'native', 'prompt'];
const ACTION_PLACES = ['header', 'menu', 'row', 'card'];
const TRIAGE = ['browserCheckable', 'unitOnly', 'gap', 'falsePositive'];

const KEYS = {
  page: ['id', 'path', 'heading', 'subtitle', 'backend', 'sidebar', 'searchPlaceholder', 'actions',
    'forms', 'tables', 'cards', 'emptyState', 'expectedErrors', 'notes'],
  form: ['id', 'scope', 'flavor', 'open', 'submit', 'cancel', 'serverError', 'modes', 'fields'],
  field: ['label', 'kind', 'ariaLabel', 'required', 'placeholder', 'default', 'sample', 'editSample',
    'options', 'normalize', 'persist', 'validators', 'source', 'requiresPick', 'minChars',
    'inputFormat', 'apiFormat', 'hypothesis'],
  validator: ['kind', 'sample', 'expectError', 'gatesSubmit', 'hypothesis'],
  scope: ['kind', 'title', 'selector', 'rowText', 'within'],
  action: ['name', 'in', 'menuTrigger', 'ariaLabel', 'css', 'enabledWhen', 'visibleWhen',
    'titleDisabled', 'confirm', 'effect', 'navigatesTo', 'opensDialog', 'hypothesis'],
  confirm: ['kind', 'title', 'message', 'confirmText', 'cancelText'],
  table: ['scope', 'columns', 'sortable', 'pageSizes', 'rowRole'],
  card: ['title', 'labels', 'emptyText', 'optional'],
  hypothesis: ['id', 'claim', 'file', 'triage', 'checkId', 'class', 'note'],
};

function onlyKeys(obj, allowed, path) {
  if (obj === null || typeof obj !== 'object' || Array.isArray(obj)) {
    throw new SpecError(path, 'expected an object');
  }
  for (const key of Object.keys(obj)) {
    if (!allowed.includes(key)) {
      throw new SpecError(path, `unknown key '${key}' (allowed: ${allowed.join(', ')})`);
    }
  }
}

function oneOf(value, allowed, path) {
  if (!allowed.includes(value)) {
    throw new SpecError(path, `'${value}' is not one of ${allowed.join(' | ')}`);
  }
}

const isText = (v) => typeof v === 'string' || v instanceof RegExp;

function checkScope(scope, path) {
  onlyKeys(scope, KEYS.scope, path);
  oneOf(scope.kind, SCOPE_KINDS, `${path}.kind`);
  if ((scope.kind === 'dialog' || scope.kind === 'card') && !isText(scope.title)) {
    throw new SpecError(path, `a '${scope.kind}' scope needs a title`);
  }
  if (scope.kind === 'css' && typeof scope.selector !== 'string') {
    throw new SpecError(path, "a 'css' scope needs a selector");
  }
  if (scope.kind === 'row' && !isText(scope.rowText)) {
    throw new SpecError(path, "a 'row' scope needs rowText");
  }
  if (scope.within) checkScope(scope.within, `${path}.within`);
}

function checkField(field, path) {
  onlyKeys(field, KEYS.field, path);
  if (typeof field.label !== 'string' || !field.label) throw new SpecError(path, 'label is required');
  oneOf(field.kind, FIELD_KINDS, `${path}.kind`);
  (field.options ?? []).forEach((opt, i) => {
    const p = `${path}.options[${i}]`;
    if (opt === null || typeof opt !== 'object' || typeof opt.label !== 'string' || !('value' in opt)) {
      throw new SpecError(p, 'options must be {label, value} pairs');
    }
  });
  (field.validators ?? []).forEach((v, i) => {
    const p = `${path}.validators[${i}]`;
    onlyKeys(v, KEYS.validator, p);
    oneOf(v.kind, VALIDATOR_KINDS, `${p}.kind`);
    if (!('expectError' in v)) throw new SpecError(p, 'expectError is required (use null for none)');
    if (v.expectError === null && !v.hypothesis) {
      throw new SpecError(p, 'expectError:null claims a defect and must name its hypothesis');
    }
  });
}

function checkAction(action, path) {
  onlyKeys(action, KEYS.action, path);
  if (typeof action.name !== 'string' || !action.name) throw new SpecError(path, 'name is required');
  oneOf(action.in, ACTION_PLACES, `${path}.in`);
  if (action.confirm) {
    onlyKeys(action.confirm, KEYS.confirm, `${path}.confirm`);
    oneOf(action.confirm.kind, CONFIRM_KINDS, `${path}.confirm.kind`);
  }
}

/**
 * Validate a page spec. Returns the spec unchanged so callers can `export default validateSpec({...})`.
 * @param {PageSpec} spec
 * @returns {PageSpec}
 */
export function validateSpec(spec) {
  const root = spec?.id ? `spec(${spec.id})` : 'spec';
  onlyKeys(spec, KEYS.page, root);
  for (const key of ['id', 'path']) {
    if (typeof spec[key] !== 'string' || !spec[key]) throw new SpecError(root, `${key} is required`);
  }
  if (!isText(spec.heading)) throw new SpecError(root, 'heading is required (string or RegExp)');

  (spec.forms ?? []).forEach((form, i) => {
    const p = `${root}.forms[${i}]`;
    onlyKeys(form, KEYS.form, p);
    if (typeof form.id !== 'string' || !form.id) throw new SpecError(p, 'id is required');
    if (!form.scope) throw new SpecError(p, 'scope is required');
    checkScope(form.scope, `${p}.scope`);
    if (form.flavor) oneOf(form.flavor, ['reactive', 'signal'], `${p}.flavor`);
    if (form.serverError) oneOf(form.serverError, ['banner', 'snackbar', 'both'], `${p}.serverError`);
    if (!Array.isArray(form.fields)) throw new SpecError(p, 'fields must be an array');
    form.fields.forEach((f, j) => checkField(f, `${p}.fields[${j}]`));
  });

  (spec.actions ?? []).forEach((a, i) => checkAction(a, `${root}.actions[${i}]`));

  (spec.tables ?? []).forEach((t, i) => {
    const p = `${root}.tables[${i}]`;
    onlyKeys(t, KEYS.table, p);
    if (!Array.isArray(t.columns)) throw new SpecError(p, 'columns must be an array');
    if (t.scope) checkScope(t.scope, `${p}.scope`);
  });

  (spec.cards ?? []).forEach((c, i) => {
    const p = `${root}.cards[${i}]`;
    onlyKeys(c, KEYS.card, p);
    if (typeof c.title !== 'string') throw new SpecError(p, 'title is required');
  });

  return spec;
}

/**
 * @param {Hypothesis} h
 * @returns {Hypothesis}
 */
export function validateHypothesis(h) {
  const path = h?.id ? `hypothesis(${h.id})` : 'hypothesis';
  onlyKeys(h, KEYS.hypothesis, path);
  for (const key of ['id', 'claim', 'file']) {
    if (typeof h[key] !== 'string' || !h[key]) throw new SpecError(path, `${key} is required`);
  }
  oneOf(h.triage, TRIAGE, `${path}.triage`);
  if (h.triage === 'browserCheckable' && !h.checkId) {
    throw new SpecError(path, 'browserCheckable hypotheses need a checkId');
  }
  return h;
}
