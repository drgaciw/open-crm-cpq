/**
 * Contacts — list, create/edit form, detail.
 * Source: frontend/libs/crm/contacts/src/lib/**
 */
import { validateSpec } from '../contract.mjs';

const text = (label, apiPath, extra = {}) => ({
  label, kind: 'input', normalize: 'trim', persist: { create: true, update: true, apiPath }, ...extra,
});

/** Unique per process: the backend rejects a second live contact with the same email. */
const stamp = Date.now().toString(36);

export const list = validateSpec({
  id: 'contacts',
  path: '/contacts',
  heading: 'Contacts',
  subtitle: 'Manage your contact database',
  backend: 'crm',
  sidebar: 'Contacts',
  searchPlaceholder: 'Search contacts...',
  actions: [
    { name: 'New Contact', in: 'header', navigatesTo: '/contacts/new' },
    { name: 'Edit', in: 'menu', navigatesTo: '/contacts/:id/edit', hypothesis: 'H-CRM-014' },
    {
      name: 'Delete', in: 'menu',
      confirm: { kind: 'mat', title: 'Delete contact', message: /^Delete ".+"\?$/, confirmText: 'Delete', cancelText: 'Cancel' },
      hypothesis: 'H-CRM-014',
    },
  ],
  tables: [{ columns: ['Name', 'Email', 'Phone', 'Organization', 'Title'], sortable: ['Name', 'Email'], pageSizes: [10, 20, 50] }],
  emptyState: {
    title: 'No contacts yet',
    message: 'Add your first contact to get started.',
    action: 'Create Contact',
  },
  forms: [{
    id: 'contact',
    scope: { kind: 'page' },
    flavor: 'reactive',
    open: { path: '/contacts/new' },
    submit: { name: 'Create' },
    cancel: { name: 'Cancel' },
    serverError: 'snackbar',
    modes: {
      create: { path: '/contacts/new', heading: 'New Contact', submit: 'Create' },
      edit: { heading: 'Edit Contact', submit: 'Update' },
    },
    fields: [
      text('First Name', 'firstName', {
        required: true, sample: 'Uat', editSample: 'Uatedit',
        validators: [{ kind: 'required', sample: '', expectError: 'Required' }],
      }),
      text('Last Name', 'lastName', {
        required: true, sample: 'Sample', // replaced with a run-unique name by the CRUD runner
        validators: [{ kind: 'required', sample: '', expectError: 'Required' }],
      }),
      text('Email', 'email', {
        sample: `uat.crud.${stamp}@example.com`, editSample: `uat.crud.${stamp}.edit@example.com`,
        validators: [{ kind: 'email', sample: 'not-an-email', expectError: 'Invalid email' }],
      }),
      text('Phone', 'phone', { sample: '+1 (555) 010-0142', editSample: '+1 (555) 010-0143' }),
      text('Job Title', 'jobTitle', { placeholder: 'e.g. VP of Sales', sample: 'VP of Testing' }),
      text('Department', 'department', { sample: 'Quality', editSample: 'Engineering' }),
      {
        // sample is injected by the suite (name of an organization fixture).
        label: 'Organization', kind: 'autocomplete', requiresPick: true, minChars: 2,
        source: { entity: 'organization', searchUrl: /\/api\/v1\/organizations\/search/ },
        persist: { create: true, update: true, apiPath: 'organizationName' },
      },
      { label: 'Notes', kind: 'textarea', normalize: 'trim', sample: 'Created by the UAT.', editSample: 'Edited by the UAT.',
        persist: { create: true, update: true, apiPath: 'notes' } },
    ],
  }],
});

export const detail = validateSpec({
  id: 'contacts.detail',
  path: '/contacts/:id',
  heading: /.+/,
  backend: 'crm',
  actions: [
    { name: 'Edit', in: 'header', navigatesTo: '/contacts/:id/edit' },
    {
      name: 'Delete', in: 'header',
      confirm: { kind: 'native', message: /^Delete ".+"\?$/ },
      effect: { navigate: '/contacts' },
    },
  ],
  cards: [
    { title: 'Contact Information', labels: ['Email', 'Phone', 'Department'] },
    { title: 'Organization', optional: true },
    { title: 'Notes', optional: true },
  ],
  notes: 'Organization and Notes cards render only when the contact has them; the suite seeds a fixture that has both and asserts them separately.',
});

export default { list, detail };
