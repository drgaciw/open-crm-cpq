/**
 * Quotes — list, create form, detail page (actions, header edit, line items, documents).
 * Source: frontend/libs/cpq/quotes/src/lib/**
 *
 * The detail-page forms cannot be driven by `runFieldContract` (it opens a form by a static
 * path; these live on /quotes/:id inside cards and rows), so they are declared here as data and
 * interpreted by workflows/quote-lifecycle.mjs.
 */
import { validateSpec } from '../contract.mjs';

export const STATUSES = ['DRAFT', 'IN_REVIEW', 'PENDING_APPROVAL', 'APPROVED', 'SENT', 'ACCEPTED',
  'EXPIRED', 'CANCELLED', 'REJECTED'];
export const DEAL_TYPES = ['NEW', 'AMENDMENT', 'RENEWAL', 'EXPANSION'];

/** Friendly labels the status badge renders (shared-models QUOTE_STATUS_LABELS). */
export const STATUS_LABELS = {
  DRAFT: 'Draft', IN_REVIEW: 'In Review', PENDING_APPROVAL: 'Pending Approval', APPROVED: 'Approved',
  SENT: 'Sent', ACCEPTED: 'Accepted', EXPIRED: 'Expired', CANCELLED: 'Cancelled', REJECTED: 'Rejected',
};

/** Filter selects on the list page: located by aria-label, not by their mat-label. */
export const filters = {
  status: {
    label: 'Status', ariaLabel: 'Filter by status', kind: 'select',
    options: [{ label: 'All statuses', value: '' }, ...STATUSES.map((s) => ({ label: s, value: s }))],
  },
  dealType: {
    label: 'Deal type', ariaLabel: 'Filter by deal type', kind: 'select',
    options: [{ label: 'All deal types', value: '' }, ...DEAL_TYPES.map((t) => ({ label: t, value: t }))],
  },
};

const text = (label, apiPath, extra = {}) => ({
  label, kind: 'input', normalize: 'trim', persist: { create: true, apiPath }, ...extra,
});

export const list = validateSpec({
  id: 'quotes',
  path: '/quotes',
  heading: 'Quotes',
  subtitle: 'Browse quotes from quote-service via gateway',
  backend: 'quote',
  sidebar: 'Quotes',
  searchPlaceholder: 'Search by name, number, or customer...',
  actions: [
    { name: 'New Quote', in: 'header', navigatesTo: '/quotes/new' },
  ],
  tables: [{
    columns: ['Quote #', 'Name', 'Customer', 'Status', 'Deal type', 'Total'],
    sortable: ['Quote #', 'Name', 'Customer', 'Status', 'Deal type', 'Total'],
    pageSizes: [10, 25, 50, 100],
  }],
  emptyState: {
    title: 'No quotes yet',
    message: 'Quotes will appear here once loaded.',
    action: 'New Quote',
  },
  forms: [{
    id: 'quote',
    scope: { kind: 'page' },
    flavor: 'reactive',
    open: { path: '/quotes/new' },
    submit: { name: 'Create quote' },
    cancel: { name: 'Cancel' },
    serverError: 'both',
    modes: {
      create: { path: '/quotes/new', heading: 'New Quote', submit: 'Create quote' },
    },
    fields: [
      text('Name', 'name', {
        required: true,
        sample: 'Quote', // replaced with a run-unique name by the CRUD runner
        validators: [{ kind: 'required', sample: '', expectError: 'Name is required' }],
      }),
      text('Customer external ID', 'customerExternalId', {
        required: true,
        sample: 'CUST-GOLD-001',
        validators: [{ kind: 'required', sample: '', expectError: 'Customer external ID is required' }],
      }),
      text('Currency', 'currency', { placeholder: 'USD', sample: 'EUR', normalize: 'upper' }),
      { label: 'Duration (months)', kind: 'number', sample: 24, persist: { create: true, apiPath: 'durationInMonths' } },
      text('Deal external ID', 'dealExternalId', { sample: 'DEAL-UAT-1' }),
      text('Region', 'region', { placeholder: 'NA', sample: 'EMEA' }),
    ],
  }],
  notes: 'Search is client-side over the current API page; Status / Deal type filter server-side.',
});

const MENU = 'More quote actions';

export const detail = validateSpec({
  id: 'quotes.detail',
  path: '/quotes/:id',
  heading: /.+/,
  backend: 'quote',
  actions: [
    { name: 'Back to quotes', in: 'header', effect: { navigate: '/quotes' } },
    {
      name: 'Calculate', in: 'header', enabledWhen: ['DRAFT', 'REJECTED'],
      titleDisabled: 'Calculate is available for DRAFT or REJECTED quotes',
    },
    {
      name: 'Submit', in: 'header', enabledWhen: ['DRAFT'],
      titleDisabled: 'Submit is available only for DRAFT quotes',
      effect: { status: 'IN_REVIEW' },
    },
    {
      name: 'Send', in: 'header', enabledWhen: ['APPROVED'],
      titleDisabled: 'Send is available only for APPROVED quotes',
      effect: { status: 'SENT' },
    },
    {
      name: 'Accept', in: 'header', enabledWhen: ['SENT'],
      titleDisabled: 'Accept is available only for SENT quotes',
      effect: { status: 'ACCEPTED' },
    },
    {
      name: 'Approve', in: 'menu', menuTrigger: MENU, enabledWhen: ['IN_REVIEW', 'PENDING_APPROVAL'],
      // Keep in sync with quotes-detail.component.ts disabled [title] copy.
      titleDisabled: 'Approve is available in review; pending approvals are decided in Approvals',
      effect: { status: 'APPROVED' },
    },
    {
      name: 'Reject', in: 'menu', menuTrigger: MENU, enabledWhen: ['IN_REVIEW', 'PENDING_APPROVAL'],
      titleDisabled: 'Reject is available in review; pending approvals are decided in Approvals',
      confirm: { kind: 'prompt', message: 'Rejection reason (optional):' },
      effect: { status: 'REJECTED' },
      hypothesis: 'H-CPQ-Q-005',
    },
    {
      name: 'Expire', in: 'menu', menuTrigger: MENU, enabledWhen: ['SENT'],
      titleDisabled: 'Expire is available only for SENT quotes',
      confirm: {
        kind: 'mat', title: 'Expire quote',
        message: /^Mark quote #\d+ as expired\? This applies only to SENT quotes\.$/m,
        confirmText: 'Expire', cancelText: 'Keep',
      },
      effect: { status: 'EXPIRED' },
    },
    {
      name: 'Cancel quote', in: 'menu', menuTrigger: MENU,
      enabledWhen: STATUSES.filter((s) => s !== 'ACCEPTED' && s !== 'CANCELLED'),
      titleDisabled: 'Cancel is unavailable for ACCEPTED or CANCELLED quotes',
      confirm: {
        kind: 'mat', title: 'Cancel quote',
        message: /Cancel quote #\d+\? Accepted quotes cannot be cancelled\./,
        confirmText: 'Cancel quote', cancelText: 'Keep',
      },
      effect: { status: 'CANCELLED' },
    },
    {
      name: 'Clone', in: 'menu', menuTrigger: MENU, enabledWhen: STATUSES,
      confirm: {
        kind: 'mat', title: 'Clone quote',
        message: /Clone quote #\d+ into a new DRAFT\?/,
        confirmText: 'Clone', cancelText: 'Cancel',
      },
      effect: { navigate: /\/quotes\/(\d+)$/ },
    },
    {
      name: 'Delete', in: 'menu', menuTrigger: MENU,
      enabledWhen: STATUSES.filter((s) => s !== 'ACCEPTED'),
      titleDisabled: 'Accepted quotes cannot be deleted',
      confirm: {
        kind: 'mat', title: 'Delete quote',
        message: /Soft-delete ".+" \(.+\)\? It will be removed from the active quote list\./,
        confirmText: 'Delete', cancelText: 'Cancel',
      },
      effect: { navigate: '/quotes' },
    },
  ],
  cards: [
    {
      title: 'Quote details',
      labels: ['Status', 'Deal type', 'Customer', 'Deal', 'Total amount', 'ARR', 'Duration', 'Region',
        'Line items', 'Description'],
    },
    { title: 'Quote header' },
    { title: 'Line items', emptyText: 'No line items yet.' },
    { title: 'Add line item', labels: ['Product external ID', 'Product name', 'List price', 'Quantity', 'Notes'] },
    { title: 'Documents', labels: ['Document type'], emptyText: 'No documents generated yet.' },
  ],
  forms: [
    {
      id: 'header',
      scope: { kind: 'card', title: 'Quote header' },
      flavor: 'reactive',
      open: { action: 'Edit' },
      submit: { name: 'Save' },
      cancel: { name: 'Cancel' },
      serverError: 'both',
      fields: [
        { label: 'Name', kind: 'input', required: true, normalize: 'trim', persist: { update: true, apiPath: 'name' },
          validators: [{ kind: 'required', sample: '', expectError: 'Name is required' }] },
        { label: 'Description', kind: 'textarea', normalize: 'trim', editSample: 'Edited by the UAT.',
          persist: { update: true, apiPath: 'description' } },
        { label: 'Deal external ID', kind: 'input', normalize: 'trim', editSample: 'DEAL-UAT-EDIT',
          persist: { update: true, apiPath: 'dealExternalId' } },
        { label: 'Subscription type', kind: 'input', normalize: 'trim', editSample: 'SAAS', // DB CHECK allows SAAS | ONPREM | HYBRID only (H-CPQ-Q-009)
          persist: { update: true, apiPath: 'subscriptionType' } },
        { label: 'Currency', kind: 'input', placeholder: 'USD', normalize: 'upper', editSample: 'gbp',
          persist: { update: true, apiPath: 'currency' } },
        { label: 'Region', kind: 'input', placeholder: 'NA', normalize: 'trim', editSample: 'APAC',
          persist: { update: true, apiPath: 'region' } },
        { label: 'Duration (months)', kind: 'number', editSample: 36,
          persist: { update: true, apiPath: 'durationInMonths' } },
        { label: 'Start date', kind: 'date', editSample: '2031-02-03', inputFormat: 'YYYY-MM-DD', apiFormat: 'YYYY-MM-DD',
          persist: { update: true, apiPath: 'startDate' } },
        { label: 'End date', kind: 'date', editSample: '2034-02-02', inputFormat: 'YYYY-MM-DD', apiFormat: 'YYYY-MM-DD',
          persist: { update: true, apiPath: 'endDate' } },
        { label: 'Notes', kind: 'input', normalize: 'trim', editSample: 'UAT header note',
          persist: { update: true, apiPath: 'notes' } },
      ],
    },
    {
      id: 'add-line',
      scope: { kind: 'card', title: 'Add line item' },
      flavor: 'reactive',
      submit: { name: 'Add line' },
      serverError: 'both',
      fields: [
        { label: 'Product external ID', kind: 'input', required: true, normalize: 'trim',
          persist: { create: true, apiPath: 'productExternalId' },
          validators: [{ kind: 'required', sample: '', expectError: 'Required' }] },
        { label: 'Product name', kind: 'input', normalize: 'trim', sample: 'UAT line',
          persist: { create: true, apiPath: 'productName' } },
        { label: 'List price', kind: 'number', required: true, sample: 25,
          persist: { create: true, apiPath: 'listPrice' },
          validators: [{ kind: 'required', sample: '', expectError: 'Required' }] },
        { label: 'Quantity', kind: 'number', default: 1, sample: 3, persist: { create: true, apiPath: 'quantity' } },
        { label: 'Notes', kind: 'input', normalize: 'trim', sample: 'UAT line note',
          persist: { create: true, apiPath: 'notes' } },
      ],
    },
    {
      id: 'edit-line',
      // rowText is a placeholder: the workflow re-scopes to the row of the product it added.
      scope: { kind: 'row', rowText: /.+/, within: { kind: 'card', title: 'Line items' } },
      flavor: 'reactive',
      open: { action: 'Edit' },
      submit: { name: 'Save' },
      cancel: { name: 'Cancel' },
      fields: [
        { label: 'Name', kind: 'input', normalize: 'trim', editSample: 'UAT line renamed',
          persist: { update: true, apiPath: 'productName' } },
        { label: 'Qty', kind: 'number', required: true, editSample: 5, persist: { update: true, apiPath: 'quantity' } },
        { label: 'List price', kind: 'number', required: true, editSample: 40,
          persist: { update: true, apiPath: 'listPrice' } },
      ],
    },
    {
      id: 'documents',
      scope: { kind: 'card', title: 'Documents' },
      flavor: 'reactive',
      submit: { name: 'Generate' },
      serverError: 'both',
      fields: [
        { label: 'Document type', kind: 'select', default: 'Proposal',
          options: [
            { label: 'Proposal', value: 'PROPOSAL' },
            { label: 'Order form', value: 'ORDER_FORM' },
            { label: 'Statement of work', value: 'SOW' },
          ] },
      ],
    },
  ],
  tables: [
    { scope: { kind: 'card', title: 'Line items' }, columns: ['Product', 'Name', 'Qty', 'List price', 'Net', 'Actions'], rowRole: 'row' },
    { scope: { kind: 'card', title: 'Documents' }, columns: ['Type', 'File', 'Size', 'Created', 'Actions'], rowRole: 'row' },
  ],
});

export default { list, detail };
