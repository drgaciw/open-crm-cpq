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
  heading: 'Opportunities',
  subtitle: 'Track deals through the pipeline',
  backend: 'crm',
  sidebar: 'Opportunities',
  // deal-list.component.ts renders "Search opportunities..." (matches the page title
  // "Opportunities" and docs/design/UI-CRM-SLDS-design-contract.md — "Opportunities (deals)").
  // "Search deals..." never matched the live input and silently broke every list check that
  // locates the search box by this placeholder (search/clear/row-click/tabs/row-menu/etc).
  searchPlaceholder: 'Search opportunities...',
  actions: [
    { name: 'Pipeline', in: 'header', navigatesTo: '/deals/kanban' },
    { name: 'New opportunity', in: 'header', navigatesTo: '/deals/new' },
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
    columns: ['Opportunity', 'Organization', 'Owner', 'Amount', 'Stage', 'Probability', 'Close Date', 'Contact'],
    sortable: ['Opportunity', 'Amount', 'Stage', 'Probability', 'Close Date'],
    pageSizes: [10, 20, 50],
  }],
  emptyState: {
    title: 'No opportunities yet',
    message: 'Create your first opportunity or convert a lead.',
    action: 'Create opportunity',
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
      create: { path: '/deals/new', heading: 'New opportunity', submit: 'Create' },
      edit: { heading: 'Edit opportunity', submit: 'Update' },
    },
    fields: [
      {
        label: 'Name', kind: 'input', required: true, normalize: 'trim',
        sample: 'Sample deal', // replaced with a run-unique name by the CRUD runner
        persist: { create: true, update: true, apiPath: 'name' },
        validators: [{ kind: 'required', sample: '', expectError: 'Required' }],
      },
      {
        label: 'Amount', kind: 'number', normalize: 'number', sample: 12500, editSample: 15000,
        persist: { create: true, update: true, apiPath: 'amount' },
        validators: [{ kind: 'min', sample: '-1', expectError: 'Amount cannot be negative' }],
      },
      {
        // Edit-only since DRG-750: a new opportunity always starts in Prospecting (H-CRM-010 resolved).
        label: 'Stage', kind: 'select', editOnly: true, editSample: 'Qualification',
        options: STAGES,
        persist: { update: true, apiPath: 'stage' },
      },
      // No Probability field since DRG-750: the API derives it from the stage (H-CRM-011 and
      // H-CRM-031 resolved; deals.form.no-probability-input asserts the absence).
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
    { title: 'Opportunity', labels: ['Amount', 'Probability', 'Expected Close', 'Created'] },
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
  heading: 'Opportunity pipeline',
  backend: 'crm',
  actions: [
    { name: 'New opportunity', in: 'header', navigatesTo: '/deals/new' },
    { name: 'Back to deals list', in: 'header', ariaLabel: 'Back to deals list', navigatesTo: '/deals' },
    // Card menu — scope by card: the aria-label repeats on every open card. No drag-and-drop exists.
    { name: 'Advance Stage', in: 'card', menuTrigger: 'Deal stage actions', visibleWhen: ADVANCEABLE },
    { name: 'Mark Won', in: 'card', menuTrigger: 'Deal stage actions', visibleWhen: OPEN, confirm: { kind: 'native', message: /^Mark ".+" as Won\?$/ } },
    { name: 'Mark Lost', in: 'card', menuTrigger: 'Deal stage actions', visibleWhen: OPEN, opensDialog: 'Mark Deal as Lost' },
  ],
  notes: 'Seven fixed columns in pipeline order, each with a count and a summed amount; cards of WON/LOST deals have no menu.',
});

export default { list, detail, kanban };
