/**
 * Deals — list (stage tabs), create/edit form, detail with lifecycle actions, the
 * "Mark Deal as Lost" dialog and the kanban board.
 * Source: frontend/libs/crm/deals/src/lib/**
 */
import { validateSpec } from '../contract.mjs';

/** Pipeline order; `label` is what STAGE_LABELS renders everywhere. */
export const STAGES = [
  { value: 'PROSPECTING', label: 'Prospecting' },
  { value: 'QUALIFICATION', label: 'Qualification' },
  { value: 'NEEDS_ANALYSIS', label: 'Needs Analysis' },
  { value: 'PROPOSAL', label: 'Proposal' },
  { value: 'NEGOTIATION', label: 'Negotiation' },
  { value: 'WON', label: 'Won' },
  { value: 'LOST', label: 'Lost' },
];
export const stageLabel = (value) => STAGES.find((s) => s.value === value)?.label ?? value;

/** Advance is offered up to PROPOSAL; NEGOTIATION must be closed through Won / Lost. */
const ADVANCEABLE = ['PROSPECTING', 'QUALIFICATION', 'NEEDS_ANALYSIS', 'PROPOSAL'];
const OPEN = [...ADVANCEABLE, 'NEGOTIATION'];
const ALL = STAGES.map((s) => s.value);

export const list = validateSpec({
  id: 'deals',
  path: '/deals',
  heading: 'Deals',
  subtitle: 'Track your sales pipeline',
  backend: 'crm',
  sidebar: 'Deals',
  searchPlaceholder: 'Search deals...',
  actions: [
    { name: 'Kanban', in: 'header', navigatesTo: '/deals/kanban' },
    { name: 'New Deal', in: 'header', navigatesTo: '/deals/new' },
    // Row menu — the trigger's aria-label is the same "Deal actions" on every row (H-A11Y-003).
    { name: 'Edit', in: 'menu', menuTrigger: 'Deal actions', visibleWhen: ALL, navigatesTo: '/deals/:id/edit' },
    { name: 'Advance Stage', in: 'menu', menuTrigger: 'Deal actions', visibleWhen: ADVANCEABLE },
    {
      name: 'Mark Won', in: 'menu', menuTrigger: 'Deal actions', visibleWhen: OPEN,
      confirm: { kind: 'native', message: /^Mark ".+" as Won\?$/ }, effect: { status: 'WON' },
    },
    { name: 'Mark Lost', in: 'menu', menuTrigger: 'Deal actions', visibleWhen: OPEN, opensDialog: 'Mark Deal as Lost', effect: { status: 'LOST' } },
    { name: 'Delete', in: 'menu', menuTrigger: 'Deal actions', visibleWhen: ALL, confirm: { kind: 'native', message: /^Delete ".+"\?$/ } },
  ],
  tables: [{
    columns: ['Deal Name', 'Amount', 'Stage', 'Probability', 'Close Date', 'Contact'],
    sortable: ['Deal Name', 'Amount', 'Stage', 'Probability', 'Close Date'],
    pageSizes: [10, 20, 50],
  }],
  emptyState: {
    title: 'No deals yet',
    message: 'Create your first deal or convert a lead.',
    action: 'Create Deal',
  },
  forms: [{
    id: 'deal',
    scope: { kind: 'page' },
    flavor: 'reactive',
    open: { path: '/deals/new' },
    submit: { name: 'Create' },
    cancel: { name: 'Cancel' },
    serverError: 'snackbar',
    modes: {
      create: { path: '/deals/new', heading: 'New Deal', submit: 'Create' },
      edit: { heading: 'Edit Deal', submit: 'Update' },
    },
    fields: [
      {
        label: 'Deal Name', kind: 'input', required: true, normalize: 'trim',
        sample: 'Sample deal', // replaced with a run-unique name by the CRUD runner
        persist: { create: true, update: true, apiPath: 'name' },
        validators: [{ kind: 'required', sample: '', expectError: 'Required' }],
      },
      {
        label: 'Amount ($)', kind: 'number', normalize: 'number', sample: 12500, editSample: 15000,
        persist: { create: true, update: true, apiPath: 'amount' },
        validators: [{ kind: 'min', sample: '-1', expectError: 'Amount cannot be negative' }],
      },
      {
        // The create request strips `stage` (H-CRM-010), so the CRUD pass creates with the default
        // and only changes the stage on edit; the dedicated check picks a non-default stage.
        label: 'Stage', kind: 'select', default: 'Prospecting', sample: 'Prospecting', editSample: 'Qualification',
        options: STAGES,
        persist: { create: 'ignored', update: true, apiPath: 'stage' },
      },
      {
        // min(0)/max(100) are declared but the template has no <mat-error> for them (H-CRM-011).
        // The value is also never sent anywhere useful: the API derives probability from the stage
        // (H-CRM-031), hence no sample / persist here.
        label: 'Probability (%)', kind: 'number', default: 10,
        validators: [
          { kind: 'min', sample: '-1', expectError: null, hypothesis: 'H-CRM-011' },
          { kind: 'max', sample: '101', expectError: null, hypothesis: 'H-CRM-011' },
        ],
      },
      {
        // Typed, never driven through the calendar. Persistence is asserted by hand because the
        // CRUD runner compares the typed text verbatim and cannot map M/D/YYYY -> YYYY-MM-DD.
        label: 'Expected Close Date', kind: 'date', inputFormat: 'M/D/YYYY', apiFormat: 'YYYY-MM-DD',
      },
      {
        label: 'Contact', kind: 'autocomplete', requiresPick: true, minChars: 2,
        source: { entity: 'contact', searchUrl: /\/api\/v1\/contacts\/search/ },
      },
      {
        // sample is injected by the suite (name of an organization fixture).
        label: 'Organization', kind: 'autocomplete', requiresPick: true, minChars: 2,
        source: { entity: 'organization', searchUrl: /\/api\/v1\/organizations\/search/ },
        persist: { create: true, update: true, apiPath: 'organizationName' },
      },
      {
        label: 'Description', kind: 'textarea', normalize: 'trim',
        sample: 'Created by the UAT.', editSample: 'Edited by the UAT.',
        persist: { create: true, update: true, apiPath: 'description' },
      },
    ],
  }],
});

export const detail = validateSpec({
  id: 'deals.detail',
  path: '/deals/:id',
  heading: /.+/,
  backend: 'crm',
  actions: [
    { name: 'Edit', in: 'header', navigatesTo: '/deals/:id/edit' },
    { name: 'Advance Stage', in: 'header', visibleWhen: ADVANCEABLE },
    { name: 'Won', in: 'header', visibleWhen: OPEN, confirm: { kind: 'native', message: 'Mark this deal as Won?' }, effect: { status: 'WON' } },
    { name: 'Lost', in: 'header', visibleWhen: OPEN, opensDialog: 'Mark Deal as Lost', effect: { status: 'LOST' } },
    // There is no Delete on this page — H-CRM-025 (gap). Deals are deleted from the list row menu.
  ],
  cards: [
    { title: 'Deal Information', labels: ['Amount', 'Probability', 'Expected Close', 'Created'] },
    { title: 'Relationships', emptyText: 'No linked contacts or organizations.' },
    { title: 'Lost Reason', optional: true },
    { title: 'Description', optional: true },
  ],
  forms: [{
    id: 'lose-reason',
    scope: { kind: 'dialog', title: 'Mark Deal as Lost' },
    flavor: 'reactive',
    open: { action: 'Lost' },
    submit: { name: 'Mark as Lost' },
    cancel: { name: 'Cancel' },
    fields: [{
      label: 'Reason', kind: 'textarea', required: true, placeholder: 'Why was this deal lost?',
      sample: 'Went with a competitor (UAT)',
      validators: [{ kind: 'required', sample: '', expectError: 'Reason is required' }],
    }],
  }],
});

export const kanban = validateSpec({
  id: 'deals.kanban',
  path: '/deals/kanban',
  heading: 'Deal Pipeline',
  backend: 'crm',
  actions: [
    { name: 'New Deal', in: 'header', navigatesTo: '/deals/new' },
    { name: 'Back to deals list', in: 'header', ariaLabel: 'Back to deals list', navigatesTo: '/deals' },
    // Card menu — scope by card: the aria-label repeats on every open card. No drag-and-drop exists.
    { name: 'Advance Stage', in: 'card', menuTrigger: 'Deal stage actions', visibleWhen: ADVANCEABLE },
    { name: 'Mark Won', in: 'card', menuTrigger: 'Deal stage actions', visibleWhen: OPEN, confirm: { kind: 'native', message: /^Mark ".+" as Won\?$/ } },
    { name: 'Mark Lost', in: 'card', menuTrigger: 'Deal stage actions', visibleWhen: OPEN, opensDialog: 'Mark Deal as Lost' },
  ],
  notes: 'Seven fixed columns in pipeline order, each with a count and a summed amount; cards of WON/LOST deals have no menu.',
});

export default { list, detail, kanban };
