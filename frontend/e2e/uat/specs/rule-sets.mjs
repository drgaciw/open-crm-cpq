/**
 * Rule sets — list, "New rule set" dialog, detail, rule dialog, rule impact dialog.
 * Source: frontend/libs/cpq/rule-sets/src/lib/**
 * (Native date inputs are covered by hand in the suite — see specs/price-books.mjs.)
 */
import { validateSpec } from '../contract.mjs';

export const STATUS_OPTIONS = ['All statuses', 'Draft', 'Active', 'Deprecated', 'Archived'];
export const RULE_TYPES = ['PRICING', 'CONFIGURATION', 'APPROVAL', 'RENEWAL', 'RISK'];
export const DATE_FIELDS = ['Effective date', 'Expiration date'];

export const IMPACT_LABELS = [
  'Rule ID', 'Name', 'Type', 'Status', 'Priority', 'Rule set ID', 'Rule set status', 'Active rule set',
  'Total rules in set', 'Active rules in set', 'Pricing executions (all-time)', 'Pricing executions (last 30d)',
];

/** Smallest DRL the backend compiler accepts — same shape as the seeded "Core Pricing Rules". */
export const validDrl = (name) => [
  `rule "${name}"`,
  'salience 100',
  'when',
  '    $quote: QuoteFact()',
  'then',
  '    $quote.setString("explanationCode", "UAT_NOOP");',
  'end',
].join('\n');

export const INVALID_DRL = 'rule "broken" when this is not valid drl ((( then end';

export const list = validateSpec({
  id: 'rule-sets',
  path: '/rule-sets',
  heading: 'Rule sets',
  subtitle: 'Browse pricing rule sets at /api/v1/rule-sets',
  backend: 'pricing',
  sidebar: 'Rule Sets',
  actions: [{ name: 'New rule set', in: 'header', opensDialog: 'New rule set' }],
  tables: [{
    columns: ['Name', 'Status', 'Engine', 'Version', 'Effective'],
    sortable: ['Name', 'Status', 'Engine', 'Version', 'Effective'],
    pageSizes: [10, 25, 50, 100],
  }],
  emptyState: { title: 'No rule sets yet', message: 'Create a rule set to get started.', action: 'New rule set' },
  forms: [{
    id: 'create',
    scope: { kind: 'dialog', title: 'New rule set' },
    flavor: 'reactive',
    open: { action: 'New rule set' },
    submit: { name: 'Create' },
    cancel: { name: 'Cancel' },
    serverError: 'both',
    fields: [
      {
        label: 'Name', kind: 'input', required: true, normalize: 'trim', sample: 'UAT rule set',
        persist: { create: true, apiPath: 'name' },
        validators: [{ kind: 'required', sample: '', expectError: 'Name is required' }],
      },
      {
        label: 'Description', kind: 'textarea', normalize: 'trim', sample: 'Created by the UAT.',
        persist: { create: true, apiPath: 'description' },
      },
      {
        label: 'Engine type', kind: 'select', default: 'DROOLS', sample: 'OPENL',
        options: [{ label: 'DROOLS', value: 'DROOLS' }, { label: 'OPENL', value: 'OPENL' }],
        persist: { create: true, apiPath: 'engineType' },
      },
    ],
  }],
});

export const lifecycleActions = [
  {
    name: 'Activate', in: 'header', css: '.activate-btn', visibleWhen: ['DRAFT'],
    confirm: {
      kind: 'mat', title: 'Activate rule set',
      message: /Activate ".+"\? Status will change from Draft to Active\./,
      confirmText: 'Activate', cancelText: 'Cancel',
    },
    effect: { status: 'ACTIVE' },
  },
  {
    name: 'Deprecate', in: 'header', css: '.deprecate-btn', visibleWhen: ['ACTIVE'],
    confirm: {
      kind: 'mat', title: 'Deprecate rule set',
      message: /Deprecate ".+"\? Status will change from Active to Deprecated\./,
      confirmText: 'Deprecate', cancelText: 'Cancel',
    },
    effect: { status: 'DEPRECATED' },
  },
];

export const deleteRuleAction = {
  name: 'Delete', in: 'row', css: '.row-delete-btn',
  confirm: {
    kind: 'mat', title: 'Delete rule',
    message: /Delete rule ".+"\? This cannot be undone from the portal\./,
    confirmText: 'Delete', cancelText: 'Cancel',
  },
};

/** Detail page of one concrete rule set; the rule dialog opens from its "Add rule" button. */
export const detailFor = (id) => validateSpec({
  id: 'rule-sets.detail',
  path: `/rule-sets/${id}`,
  heading: /.+/,
  backend: 'pricing',
  actions: [
    { name: 'Back to rule sets', in: 'header', navigatesTo: '/rule-sets' },
    ...lifecycleActions,
    { name: 'Add rule', in: 'card', css: '.add-rule-btn', opensDialog: 'New rule' },
    { name: 'Impact', in: 'row', css: '.row-impact-btn', opensDialog: 'Rule impact' },
    { name: 'Edit', in: 'row', css: '.row-edit-btn', opensDialog: 'Edit rule' },
    deleteRuleAction,
  ],
  cards: [{
    title: 'Rule set details',
    labels: ['Status', 'Engine', 'Version', 'Effective', 'Expires', 'Updated', 'Description'],
  }],
  forms: [{
    id: 'rule',
    scope: { kind: 'dialog', title: 'New rule' },
    flavor: 'reactive',
    open: { action: 'Add rule' },
    submit: { name: 'Create' },
    cancel: { name: 'Cancel' },
    serverError: 'both',
    fields: [
      {
        label: 'Name', kind: 'input', required: true, normalize: 'trim', sample: 'UAT rule',
        persist: { create: true, update: true, apiPath: 'name' },
        validators: [{ kind: 'required', sample: '', expectError: 'Name is required' }],
      },
      {
        label: 'Description', kind: 'textarea', normalize: 'trim', sample: 'UAT rule description',
        persist: { create: true, update: true, apiPath: 'description' },
      },
      {
        label: 'Rule type', kind: 'select', default: 'PRICING', sample: 'APPROVAL', editSample: 'RISK',
        options: RULE_TYPES.map((t) => ({ label: t, value: t })),
        persist: { create: true, update: true, apiPath: 'ruleType' },
        hypothesis: 'H-CPQ-PR-003',
      },
      {
        label: 'Priority (100–1000)', kind: 'number', required: true, default: 100,
        sample: '150', editSample: '250', normalize: 'number',
        persist: { create: true, update: true, apiPath: 'priority' },
        validators: [
          { kind: 'required', sample: '', expectError: 'Priority is required' },
          { kind: 'min', sample: '99', expectError: 'Priority must be between 100 and 1000' },
          { kind: 'max', sample: '1001', expectError: 'Priority must be between 100 and 1000' },
        ],
      },
      {
        label: 'DRL content', kind: 'textarea', required: true, normalize: 'trim', sample: validDrl('UAT sample'),
        persist: { create: true, update: true, apiPath: 'content' },
        validators: [{ kind: 'required', sample: '', expectError: 'DRL content is required' }],
      },
    ],
  }],
  tables: [{
    scope: { kind: 'css', selector: '.rules-table' },
    columns: ['Name', 'Type', 'Priority', 'Status', 'Actions'],
    rowRole: 'tr',
  }],
});

export default { list, detailFor };
