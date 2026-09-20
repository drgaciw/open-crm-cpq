/**
 * Pricing customers — list, "New pricing customer" dialog, detail with inline edit.
 * Source: frontend/libs/cpq/pricing-customers/src/lib/**
 */
import { validateSpec } from '../contract.mjs';

export const SEGMENTS = [
  { label: 'Startup', value: 'STARTUP' },
  { label: 'SMB', value: 'SMB' },
  { label: 'Mid-Market', value: 'MID_MARKET' },
  { label: 'Enterprise', value: 'ENTERPRISE' },
  { label: 'Strategic', value: 'STRATEGIC' },
];
export const REGIONS = [
  { label: 'North America', value: 'NA' },
  { label: 'EMEA', value: 'EMEA' },
  { label: 'APAC', value: 'APAC' },
  { label: 'LATAM', value: 'LATAM' },
];

const text = (label, apiPath, extra = {}) => ({
  label, kind: 'input', normalize: 'trim', persist: { create: true, update: true, apiPath }, ...extra,
});

/** Fields the create dialog and the inline edit form have in common. */
const sharedFields = () => [
  text('Name', 'name', {
    required: true,
    sample: 'UAT pricing customer',
    validators: [{ kind: 'required', sample: '', expectError: 'Name is required' }],
  }),
  text('Type', 'type', { placeholder: 'e.g. ACCOUNT', sample: 'ACCOUNT', editSample: 'PARTNER' }),
  text('Industry', 'industry', { sample: 'Technology', editSample: 'Finance' }),
  {
    label: 'Segment', kind: 'select', default: '—', sample: 'SMB', editSample: 'Enterprise',
    options: [{ label: '—', value: '' }, ...SEGMENTS],
    persist: { create: true, update: true, apiPath: 'segment' },
  },
  {
    label: 'Region', kind: 'select', default: '—', sample: 'EMEA', editSample: 'APAC',
    options: [{ label: '—', value: '' }, ...REGIONS],
    persist: { create: true, update: true, apiPath: 'region' },
  },
  {
    label: 'Churn score', kind: 'number', sample: '25', editSample: '40', normalize: 'number',
    persist: { create: true, update: true, apiPath: 'churnScore' },
  },
  {
    label: 'Annual revenue', kind: 'number', sample: '125000', editSample: '250000', normalize: 'number',
    persist: { create: true, update: true, apiPath: 'annualRevenue' },
  },
];

export const list = validateSpec({
  id: 'pricing-customers',
  path: '/pricing-customers',
  heading: 'Pricing customers',
  subtitle: 'Browse pricing-service customers at /api/v1/pricing-customers',
  backend: 'pricing',
  sidebar: 'Pricing Customers',
  actions: [{ name: 'New pricing customer', in: 'header', opensDialog: 'New pricing customer' }],
  tables: [{
    columns: ['Name', 'External ID', 'Segment', 'Region', 'Industry'],
    sortable: ['Name', 'External ID', 'Segment', 'Region', 'Industry'],
    pageSizes: [10, 25, 50, 100],
  }],
  emptyState: {
    title: 'No pricing customers yet',
    message: 'Create a pricing customer to get started.',
    action: 'New pricing customer',
  },
  forms: [{
    id: 'create',
    scope: { kind: 'dialog', title: 'New pricing customer' },
    flavor: 'reactive',
    open: { action: 'New pricing customer' },
    submit: { name: 'Create' },
    cancel: { name: 'Cancel' },
    serverError: 'both',
    fields: [
      text('External ID', 'externalId', {
        required: true,
        sample: 'UAT-EXT',
        persist: { create: true, update: false, apiPath: 'externalId' },
        validators: [{ kind: 'required', sample: '', expectError: 'External ID is required' }],
      }),
      ...sharedFields(),
    ],
  }],
});

/** Inline edit form on the detail card. External ID is deliberately not part of it. */
export const editForm = {
  id: 'edit',
  scope: { kind: 'card', title: 'Edit pricing customer' },
  flavor: 'reactive',
  submit: { name: 'Save' },
  cancel: { name: 'Cancel' },
  serverError: 'both',
  fields: sharedFields(),
};

export const detail = validateSpec({
  id: 'pricing-customers.detail',
  path: '/pricing-customers/:id',
  heading: /.+/,
  backend: 'pricing',
  actions: [
    { name: 'Back to pricing customers', in: 'header', navigatesTo: '/pricing-customers' },
    { name: 'Edit', in: 'header' },
    {
      name: 'Delete', in: 'header',
      confirm: {
        kind: 'mat',
        title: 'Delete pricing customer',
        message: /Soft-delete ".+" \(.+\)\? It will be removed from the pricing customers list\./,
        confirmText: 'Delete',
        cancelText: 'Cancel',
      },
      effect: { navigate: '/pricing-customers' },
    },
  ],
  cards: [{
    title: 'Pricing customer details',
    labels: ['External ID', 'Type', 'Segment', 'Region', 'Industry', 'Churn score', 'Annual revenue', 'Updated'],
  }],
});

export default { list, detail, editForm };
