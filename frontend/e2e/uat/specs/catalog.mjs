/**
 * Product catalog — list, New Product dialog (signal form), detail page and its nested dialogs.
 * Source: frontend/libs/cpq/catalog/src/lib/**  (templates are inline; strings copied verbatim)
 */
import { validateSpec } from '../contract.mjs';

export const PRODUCT_TYPES = ['SAAS_SEAT_NAMED', 'SAAS_SEAT_CONCURRENT', 'SAAS_USAGE_METERED',
  'SAAS_FLAT_RATE', 'ONPREM_PERPETUAL', 'ONPREM_CORE', 'ONPREM_SUBSCRIPTION', 'HYBRID_PORTABLE',
  'SUPPORT_BASIC', 'SUPPORT_PREMIUM', 'SUPPORT_ENTERPRISE', 'PROFESSIONAL_SERVICES', 'TRAINING',
  'ADD_ON_API', 'ADD_ON_STORAGE', 'ADD_ON_ANALYTICS', 'ADD_ON_SECURITY', 'ADD_ON_INTEGRATION',
  'HARDWARE', 'CUSTOM'];
export const CATEGORIES = ['SUBSCRIPTION', 'LICENSE', 'SUPPORT', 'SERVICES', 'ADD_ON'];
export const STATUSES = ['DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED'];
export const SELECTION_TYPES = ['SINGLE', 'MULTIPLE', 'REQUIRED_SINGLE'];
export const RULE_TYPES = ['REQUIRES', 'EXCLUDES', 'RECOMMENDS', 'DEFAULT', 'VALIDATES'];

/** The UI renders raw enum constants, so label === value everywhere (see H-CPQ-P-006). */
const raw = (values) => values.map((v) => ({ label: v, value: v }));

export const list = validateSpec({
  id: 'catalog',
  path: '/catalog',
  heading: 'Product Catalog',
  subtitle: 'Browse sellable products from the product catalog API',
  backend: 'product',
  sidebar: 'Catalog',
  searchPlaceholder: 'Search by name or SKU...',
  actions: [{ name: 'New Product', in: 'header', opensDialog: 'New Product' }],
  tables: [{
    columns: ['SKU', 'Name', 'Category', 'Status', 'List Price'],
    sortable: ['SKU', 'Name', 'Category', 'Status', 'List Price'],
    pageSizes: [10, 25, 50, 100],
  }],
  emptyState: {
    title: 'No products yet',
    message: 'Catalog is empty. Create a product to get started.',
    action: 'New Product',
  },
  forms: [
    {
      id: 'new-product',
      scope: { kind: 'dialog', title: 'New Product' },
      flavor: 'signal',
      open: { action: 'New Product' },
      submit: { name: 'Create' },
      cancel: { name: 'Cancel' },
      serverError: 'banner',
      fields: [
        {
          label: 'SKU', kind: 'input', required: true, sample: 'UAT-SKU', normalize: 'trim',
          persist: { create: true, apiPath: 'sku' },
          validators: [
            { kind: 'required', sample: '', expectError: 'SKU is required' },
            // H-CPQ-P-015: schema message exists but Signal Forms leaves Create disabled with no mat-error.
            { kind: 'maxLength', sample: 'S'.repeat(101), expectError: null, hypothesis: 'H-CPQ-P-015' },
          ],
        },
        {
          label: 'Name', kind: 'input', required: true, sample: 'UAT product', normalize: 'trim',
          persist: { create: true, update: true, apiPath: 'name' },
          validators: [
            { kind: 'required', sample: '', expectError: 'Name is required' },
            { kind: 'maxLength', sample: 'N'.repeat(256), expectError: null, hypothesis: 'H-CPQ-P-015' },
          ],
        },
        {
          label: 'Product type', kind: 'select', required: true, default: '', sample: 'SAAS_FLAT_RATE',
          persist: { create: true, update: true, apiPath: 'productType' },
          options: raw(PRODUCT_TYPES),
          validators: [{ kind: 'required', sample: '', expectError: 'Product type is required' }],
        },
        {
          label: 'Category', kind: 'select', required: true, default: '', sample: 'SUBSCRIPTION',
          persist: { create: true, update: true, apiPath: 'category' },
          options: raw(CATEGORIES),
          validators: [{ kind: 'required', sample: '', expectError: 'Category is required' }],
        },
        {
          label: 'List price', kind: 'number', sample: '49.5', normalize: 'number',
          persist: { create: true, update: true, apiPath: 'listPrice' },
          validators: [{ kind: 'min', sample: '-5', expectError: 'List price must be at least 0' }],
        },
        {
          // maxLength(3) is exercised by a hand-written check that *types* (H-CPQ-P-005):
          // fill() bypasses a maxlength attribute, real keystrokes do not.
          label: 'Currency', kind: 'input', placeholder: 'USD', sample: 'usd', normalize: 'upper',
          persist: { create: true, update: true, apiPath: 'currency' },
        },
      ],
    },
  ],
});

/**
 * Dialog forms that open from /catalog/:id. `openForm` navigates to `spec.path`, so the suite
 * wraps each one in a throw-away page spec carrying a concrete product path. One spec id per
 * dialog keeps check ids unique (three of them have a "Name" field).
 */
const dialogSpec = (id, form) => (path) => validateSpec({ id, path, heading: /.+/, backend: 'product', forms: [form] });

export const optionGroupDialog = dialogSpec('catalog.option-group', {
  id: 'new-option-group',
  scope: { kind: 'dialog', title: 'New option group' },
  flavor: 'reactive',
  open: { action: 'Create group' },
  submit: { name: 'Create' },
  cancel: { name: 'Cancel' },
  serverError: 'banner',
  fields: [
    { label: 'Name', kind: 'input', required: true, sample: 'UAT group',
      validators: [{ kind: 'required', sample: '', expectError: 'Name is required' }] },
    { label: 'Selection type', kind: 'select', required: true, default: '', sample: 'SINGLE',
      options: raw(SELECTION_TYPES),
      validators: [{ kind: 'required', sample: '', expectError: 'Selection type is required' }] },
    { label: 'Min selections', kind: 'number', default: '0' },
    { label: 'Max selections', kind: 'number', default: '' },
  ],
});

export const configRuleDialog = dialogSpec('catalog.config-rule', {
  id: 'new-config-rule',
  scope: { kind: 'dialog', title: 'New configuration rule' },
  flavor: 'reactive',
  open: { action: 'Add rule' },
  submit: { name: 'Create' },
  cancel: { name: 'Cancel' },
  serverError: 'banner',
  fields: [
    { label: 'Name', kind: 'input', required: true, sample: 'UAT rule',
      validators: [{ kind: 'required', sample: '', expectError: 'Name is required' }] },
    { label: 'Rule type', kind: 'select', required: true, default: '', sample: 'REQUIRES',
      options: raw(RULE_TYPES),
      validators: [{ kind: 'required', sample: '', expectError: 'Rule type is required' }] },
    { label: 'Target product ID (optional)', kind: 'number', default: '' },
    { label: 'Condition expression (optional)', kind: 'textarea',
      placeholder: 'Stored only — not evaluated in this UI' },
    { label: 'Description (optional)', kind: 'textarea' },
    { label: 'Priority', kind: 'number', default: '0' },
  ],
});

/** Needs a product that already has an option group (the button lives on the group card). */
export const optionDialog = dialogSpec('catalog.option', {
  id: 'add-option',
  scope: { kind: 'dialog', title: 'Add option' },
  flavor: 'reactive',
  open: { action: 'Add option' },
  submit: { name: 'Add' },
  cancel: { name: 'Cancel' },
  serverError: 'banner',
  fields: [
    { label: 'Name', kind: 'input', required: true, sample: 'UAT option',
      validators: [{ kind: 'required', sample: '', expectError: 'Name is required' }] },
    { label: 'Price adjustment', kind: 'number', default: '' },
    { label: 'Adjustment type', kind: 'select', default: '',
      options: [{ label: '—', value: null }, { label: 'ABSOLUTE', value: 'ABSOLUTE' }, { label: 'PERCENTAGE', value: 'PERCENTAGE' }] },
    { label: 'Default option', kind: 'checkbox', default: false },
  ],
});

export const detail = validateSpec({
  id: 'catalog.detail',
  path: '/catalog/:id',
  heading: /.+/,
  backend: 'product',
  actions: [
    { name: 'Back to catalog', in: 'header', navigatesTo: '/catalog' },
    { name: 'Edit', in: 'header' },
    {
      name: 'Delete', in: 'header',
      confirm: {
        kind: 'mat', title: 'Delete product',
        message: /Soft-delete ".+" \(.+\)\? It will be removed from the active catalog\./,
        confirmText: 'Delete', cancelText: 'Cancel',
      },
      effect: { navigate: '/catalog' },
    },
  ],
  cards: [
    { title: 'Product details', labels: ['Status', 'Category', 'Product type', 'List price', 'Description'] },
  ],
  forms: [{
    id: 'edit-product',
    scope: { kind: 'card', title: 'Edit product' },
    flavor: 'reactive',
    submit: { name: 'Save' },
    cancel: { name: 'Cancel' },
    serverError: 'banner',
    fields: [
      { label: 'Name', kind: 'input', required: true, normalize: 'trim', persist: { update: true, apiPath: 'name' } },
      { label: 'Description', kind: 'textarea', normalize: 'trim', editSample: 'Edited by the UAT.', persist: { update: true, apiPath: 'description' } },
      { label: 'Product type', kind: 'select', required: true, editSample: 'TRAINING', options: raw(PRODUCT_TYPES), persist: { update: true, apiPath: 'productType' } },
      { label: 'Category', kind: 'select', required: true, editSample: 'SERVICES', options: raw(CATEGORIES), persist: { update: true, apiPath: 'category' } },
      { label: 'Status', kind: 'select', required: true, editSample: 'ACTIVE', options: raw(STATUSES), persist: { update: true, apiPath: 'status' } },
      { label: 'List price', kind: 'number', editSample: '99.25', normalize: 'number', persist: { update: true, apiPath: 'listPrice' } },
      { label: 'Currency', kind: 'input', placeholder: 'USD', editSample: 'eur', normalize: 'upper', persist: { update: true, apiPath: 'currency' } },
    ],
  }],
});

export default { list, detail };
