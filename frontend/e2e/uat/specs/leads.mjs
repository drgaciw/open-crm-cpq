/**
 * Leads — list (status tabs + priority filter), create/edit form, detail with lifecycle actions,
 * and the "Mark Lead as Lost" dialog.
 * Source: frontend/libs/crm/leads/src/lib/**
 */
import { validateSpec } from '../contract.mjs';

export const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

/** Tab label -> status query parameter (undefined = no filter). Order matches the tab group. */
export const STATUS_TABS = [
  { label: 'All', status: undefined },
  { label: 'New', status: 'NEW' },
  { label: 'Contacted', status: 'CONTACTED' },
  { label: 'Qualified', status: 'QUALIFIED' },
  { label: 'Unqualified', status: 'UNQUALIFIED' },
  { label: 'Converted', status: 'CONVERTED' },
  { label: 'Lost', status: 'LOST' },
];

/** The list page's priority filter — a select outside any form, so it lives beside the spec. */
export const priorityFilter = {
  label: 'Priority', ariaLabel: 'Filter by priority', kind: 'select', default: 'All priorities',
  options: [{ label: 'All priorities', value: '' }, ...PRIORITIES.map((p) => ({ label: p, value: p }))],
};

const OPEN = ['NEW', 'CONTACTED', 'QUALIFIED'];

export const list = validateSpec({
  id: 'leads',
  path: '/leads',
  heading: 'Leads',
  subtitle: 'Manage your lead pipeline',
  backend: 'crm',
  sidebar: 'Leads',
  searchPlaceholder: 'Search leads...',
  actions: [
    { name: 'New Lead', in: 'header', navigatesTo: '/leads/new' },
    // Row menu (trigger aria-label is "Actions for <title>").
    { name: 'Edit', in: 'menu', visibleWhen: ['NEW', 'CONTACTED', 'QUALIFIED', 'UNQUALIFIED'], navigatesTo: '/leads/:id/edit' },
    { name: 'Mark Contacted', in: 'menu', visibleWhen: ['NEW'], effect: { status: 'CONTACTED' } },
    { name: 'Qualify', in: 'menu', visibleWhen: ['CONTACTED'], effect: { status: 'QUALIFIED' } },
    {
      name: 'Convert to Deal', in: 'menu', visibleWhen: ['QUALIFIED'],
      confirm: { kind: 'mat', title: 'Convert lead', message: /^Convert ".+" to a deal\?$/, confirmText: 'Convert', cancelText: 'Cancel' },
      effect: { status: 'CONVERTED', navigate: /\/deals\/\d+$/ },
    },
    { name: 'Mark Lost', in: 'menu', visibleWhen: OPEN, opensDialog: 'Mark Lead as Lost', effect: { status: 'LOST' } },
    {
      name: 'Delete', in: 'menu', visibleWhen: ['NEW', 'CONTACTED', 'QUALIFIED', 'UNQUALIFIED', 'LOST'],
      confirm: { kind: 'mat', title: 'Delete lead', message: /^Delete lead ".+"\?$/, confirmText: 'Delete', cancelText: 'Cancel' },
    },
  ],
  tables: [{
    columns: ['Title', 'Status', 'Priority', 'Source', 'Estimated Value', 'Contact', 'Organization'],
    sortable: ['Title', 'Status', 'Priority', 'Source', 'Estimated Value'],
    pageSizes: [10, 20, 50],
  }],
  emptyState: {
    title: 'No leads yet',
    message: 'Add your first lead to start tracking opportunities.',
    action: 'Create Lead',
  },
  forms: [{
    id: 'lead',
    scope: { kind: 'page' },
    flavor: 'reactive',
    open: { path: '/leads/new' },
    submit: { name: 'Create' },
    cancel: { name: 'Cancel' },
    serverError: 'snackbar',
    modes: {
      create: { path: '/leads/new', heading: 'New Lead', submit: 'Create' },
      edit: { heading: 'Edit Lead', submit: 'Update' },
    },
    fields: [
      {
        label: 'Title', kind: 'input', required: true, normalize: 'trim',
        placeholder: 'e.g. Acme Corp - CRM Implementation',
        sample: 'Sample lead', // replaced with a run-unique title by the CRUD runner
        persist: { create: true, update: true, apiPath: 'title' },
        validators: [{ kind: 'required', sample: '', expectError: 'Required' }],
      },
      {
        label: 'Source', kind: 'input', normalize: 'trim', placeholder: 'e.g. Website, Referral, Conference',
        sample: 'Conference', editSample: 'Referral',
        persist: { create: true, update: true, apiPath: 'source' },
      },
      {
        label: 'Priority', ariaLabel: 'Lead priority', kind: 'select', default: 'MEDIUM',
        sample: 'HIGH', editSample: 'URGENT',
        options: PRIORITIES.map((p) => ({ label: p, value: p })),
        persist: { create: true, update: true, apiPath: 'priority' },
      },
      {
        // No Validators.min(0) although the API is @PositiveOrZero — see H-CRM-016 (checked by hand:
        // the contract cannot express "an invalid sample that the form accepts").
        label: 'Estimated Value', kind: 'number', placeholder: '0', normalize: 'number',
        sample: 2500, editSample: 3750,
        persist: { create: true, update: true, apiPath: 'estimatedValue' },
      },
      {
        label: 'Description', kind: 'textarea', normalize: 'trim', placeholder: 'Describe the opportunity...',
        sample: 'Created by the UAT.', editSample: 'Edited by the UAT.',
        persist: { create: true, update: true, apiPath: 'description' },
      },
      {
        // Picked through a hand-written check: the option text is "First Last email", which the
        // CRUD runner cannot compare against a single API field.
        label: 'Contact', kind: 'autocomplete', placeholder: 'Search for contact...', requiresPick: true, minChars: 2,
        source: { entity: 'contact', searchUrl: /\/api\/v1\/contacts\/search/ },
      },
      {
        // sample is injected by the suite (name of an organization fixture).
        label: 'Organization', kind: 'autocomplete', placeholder: 'Search for organization...', requiresPick: true, minChars: 2,
        source: { entity: 'organization', searchUrl: /\/api\/v1\/organizations\/search/ },
        persist: { create: true, update: true, apiPath: 'organizationName' },
      },
    ],
  }],
});

export const detail = validateSpec({
  id: 'leads.detail',
  path: '/leads/:id',
  heading: /.+/,
  backend: 'crm',
  actions: [
    { name: 'Edit', in: 'header', visibleWhen: ['NEW', 'CONTACTED', 'QUALIFIED', 'UNQUALIFIED'], navigatesTo: '/leads/:id/edit' },
    {
      name: 'Delete', in: 'header', visibleWhen: ['NEW', 'CONTACTED', 'QUALIFIED', 'UNQUALIFIED', 'LOST'],
      confirm: { kind: 'native', message: /^Delete lead ".+"\?$/ },
      effect: { navigate: '/leads' },
    },
    { name: 'Mark Contacted', in: 'header', visibleWhen: ['NEW'], effect: { status: 'CONTACTED' } },
    { name: 'Qualify', in: 'header', visibleWhen: ['CONTACTED'], effect: { status: 'QUALIFIED' } },
    {
      name: 'Convert to Deal', in: 'header', visibleWhen: ['QUALIFIED'],
      confirm: { kind: 'native', message: /^Convert ".+" to a deal\?$/ },
      effect: { status: 'CONVERTED', navigate: /\/deals\/(\d+)$/ },
    },
    { name: 'Mark Lost', in: 'header', visibleWhen: OPEN, opensDialog: 'Mark Lead as Lost', effect: { status: 'LOST' } },
  ],
  cards: [
    { title: 'Lead Information', labels: ['Source', 'Estimated Value', 'Created', 'Updated'] },
    { title: 'Contact', optional: true },
    { title: 'Organization', optional: true },
    { title: 'Description', optional: true },
    { title: 'Lost Reason', optional: true },
  ],
  forms: [{
    id: 'lose-reason',
    scope: { kind: 'dialog', title: 'Mark Lead as Lost' },
    flavor: 'reactive',
    open: { action: 'Mark Lost' },
    submit: { name: 'Mark as Lost' },
    cancel: { name: 'Cancel' },
    fields: [{
      label: 'Reason', kind: 'textarea', required: true, placeholder: 'Why was this lead lost?',
      sample: 'Budget was cut (UAT)',
      validators: [{ kind: 'required', sample: '', expectError: 'Reason is required' }],
    }],
  }],
  notes: 'Banners: CONVERTED -> "This lead has been converted to a deal" + "View Deal" link; LOST -> "This lead was marked as lost".',
});

export const BANNERS = {
  CONVERTED: 'This lead has been converted to a deal',
  LOST: 'This lead was marked as lost',
};

export default { list, detail };
