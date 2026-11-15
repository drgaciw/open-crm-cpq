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
/** List Status filter labels (PRODUCT_STATUS_LABELS, H-CPQ-P-006); the edit form shows them too. */
export const STATUS_FILTER_LABELS = ['Draft', 'Active', 'Deprecated', 'Retired'];
export const SELECTION_TYPES = ['SINGLE', 'MULTIPLE', 'REQUIRED_SINGLE'];
export const RULE_TYPES = ['REQUIRES', 'EXCLUDES', 'RECOMMENDS', 'DEFAULT', 'VALIDATES'];

/** Option groups and config rules still render raw enum constants, so label === value there (H-CPQ-P-006). */
const raw = (values) => values.map((v) => ({ label: v, value: v }));

/**
 * The product create and edit forms show labels since the Jev + Playwright MCP UAT (2026-11-15).
 * Copied from shared-models (CATALOG_PRODUCT_TYPE_LABELS, PRODUCT_CATEGORY_LABELS, PRODUCT_STATUS_LABELS).
 */
const TYPE_LABELS = {
  SAAS_SEAT_NAMED: 'SaaS Named Seat', SAAS_SEAT_CONCURRENT: 'SaaS Concurrent Seat', SAAS_USAGE_METERED: 'SaaS Usage Metered',
  SAAS_FLAT_RATE: 'SaaS Flat Rate', ONPREM_PERPETUAL: 'On-Prem Perpetual', ONPREM_CORE: 'On-Prem Core',
  ONPREM_SUBSCRIPTION: 'On-Prem Subscription', HYBRID_PORTABLE: 'Hybrid Portable', SUPPORT_BASIC: 'Support Basic',
  SUPPORT_PREMIUM: 'Support Premium', SUPPORT_ENTERPRISE: 'Support Enterprise', PROFESSIONAL_SERVICES: 'Professional Services',
  TRAINING: 'Training', ADD_ON_API: 'Add-on API', ADD_ON_STORAGE: 'Add-on Storage', ADD_ON_ANALYTICS: 'Add-on Analytics',
  ADD_ON_SECURITY: 'Add-on Security', ADD_ON_INTEGRATION: 'Add-on Integration', HARDWARE: 'Hardware', CUSTOM: 'Custom',
};
const CATEGORY_LABELS = { SUBSCRIPTION: 'Subscription', LICENSE: 'License', SUPPORT: 'Support', SERVICES: 'Services', ADD_ON: 'Add-on' };
const STATUS_LABELS = { DRAFT: 'Draft', ACTIVE: 'Active', DEPRECATED: 'Deprecated', RETIRED: 'Retired' };
const labelled = (values, labels) => values.map((v) => ({ label: labels[v], value: v }));

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
          // maxLength(100) is a hand-written typing check (H-CPQ-P-015): the native maxlength
          // attribute stops input at the limit, so a fill() sample never reaches the validator.
          validators: [{ kind: 'required', sample: '', expectError: 'SKU is required' }],
        },
        {
          label: 'Name', kind: 'input', required: true, sample: 'UAT product', normalize: 'trim',
          persist: { create: true, update: true, apiPath: 'name' },
          // maxLength(255): hand-written typing check, as for SKU (H-CPQ-P-015).
          validators: [{ kind: 'required', sample: '', expectError: 'Name is required' }],
        },
        {
          label: 'Product type', kind: 'select', required: true, default: '', sample: 'SaaS Flat Rate',
          persist: { create: true, update: true, apiPath: 'productType' },
          options: labelled(PRODUCT_TYPES, TYPE_LABELS),
          validators: [{ kind: 'required', sample: '', expectError: 'Product type is required' }],
        },
        {
          label: 'Category', kind: 'select', required: true, default: '', sample: 'Subscription',
          persist: { create: true, update: true, apiPath: 'category' },
          options: labelled(CATEGORIES, CATEGORY_LABELS),
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
      { label: 'Product type', kind: 'select', required: true, editSample: 'Training', options: labelled(PRODUCT_TYPES, TYPE_LABELS), persist: { update: true, apiPath: 'productType' } },
      { label: 'Category', kind: 'select', required: true, editSample: 'Services', options: labelled(CATEGORIES, CATEGORY_LABELS), persist: { update: true, apiPath: 'category' } },
      { label: 'Status', kind: 'select', required: true, editSample: 'Active', options: labelled(STATUSES, STATUS_LABELS), persist: { update: true, apiPath: 'status' } },
      { label: 'List price', kind: 'number', editSample: '99.25', normalize: 'number', persist: { update: true, apiPath: 'listPrice' } },
      { label: 'Currency', kind: 'input', placeholder: 'USD', editSample: 'eur', normalize: 'upper', persist: { update: true, apiPath: 'currency' } },
    ],
  }],
});

export default { list, detail };
