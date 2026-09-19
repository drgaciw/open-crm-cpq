/**
 * Organizations — list, create/edit form, detail.
 * Source: frontend/libs/crm/organizations/src/lib/**
 */
import { validateSpec } from '../contract.mjs';

const INDUSTRIES = ['Technology', 'Healthcare', 'Finance', 'Education', 'Manufacturing', 'Retail',
  'Real Estate', 'Consulting', 'Media', 'Telecommunications', 'Energy', 'Transportation',
  'Government', 'Non-Profit', 'Other'];

const text = (label, apiPath, extra = {}) => ({
  label, kind: 'input', normalize: 'trim', persist: { create: true, update: true, apiPath }, ...extra,
});

export const list = validateSpec({
  id: 'organizations',
  path: '/organizations',
  heading: 'Organizations',
  subtitle: 'Manage your business relationships',
  backend: 'crm',
  sidebar: 'Organizations',
  searchPlaceholder: 'Search organizations...',
  actions: [
    { name: 'New Organization', in: 'header', navigatesTo: '/organizations/new' },
  ],
  tables: [{ columns: ['Name', 'Industry', 'City', 'Phone'], sortable: ['Name', 'Industry', 'City'], pageSizes: [10, 20, 50] }],
  emptyState: {
    title: 'No organizations yet',
    message: 'Create your first organization to get started.',
    action: 'Create Organization',
  },
  forms: [{
    id: 'organization',
    scope: { kind: 'page' },
    flavor: 'reactive',
    open: { path: '/organizations/new' },
    submit: { name: 'Create' },
    cancel: { name: 'Cancel' },
    serverError: 'snackbar',
    modes: {
      create: { path: '/organizations/new', heading: 'New Organization', submit: 'Create' },
      edit: { heading: 'Edit Organization', submit: 'Update' },
    },
    fields: [
      text('Organization Name', 'name', {
        required: true,
        placeholder: 'Enter organization name',
        sample: 'Acme', // replaced with a run-unique name by the CRUD runner
        validators: [{ kind: 'required', sample: '', expectError: 'Name is required' }],
      }),
      {
        label: 'Industry', kind: 'select', sample: 'Technology', editSample: 'Finance',
        persist: { create: true, update: true, apiPath: 'industry' },
        options: [{ label: 'None', value: '' }, ...INDUSTRIES.map((i) => ({ label: i, value: i }))],
      },
      text('Website', 'website', { placeholder: 'https://example.com', sample: 'https://uat.example.com' }),
      text('Phone', 'phone', { placeholder: '+1 (555) 000-0000', sample: '+1 (555) 010-0199', editSample: '+1 (555) 010-0200' }),
      text('Address Line 1', 'addressLine1', { sample: '1 Test Way' }),
      text('Address Line 2', 'addressLine2', { sample: 'Suite 9' }),
      text('City', 'city', { sample: 'Austin', editSample: 'Denver' }),
      text('State', 'state', { sample: 'TX' }),
      text('Postal Code', 'postalCode', { sample: '78701' }),
      text('Country', 'country', { sample: 'USA' }),
      { label: 'Description', kind: 'textarea', normalize: 'trim', sample: 'Created by the UAT.',
        persist: { create: true, update: true, apiPath: 'description' } },
    ],
  }],
});

export const detail = validateSpec({
  id: 'organizations.detail',
  path: '/organizations/:id',
  heading: /.+/,
  backend: 'crm',
  actions: [
    { name: 'Edit', in: 'header', navigatesTo: '/organizations/:id/edit' },
    {
      name: 'Delete', in: 'header',
      confirm: { kind: 'native', message: /^Delete ".+"\?$/ },
      effect: { navigate: '/organizations' },
    },
  ],
  cards: [
    { title: 'Contact Information', labels: ['Phone', 'Website'] },
    { title: 'Address', labels: ['Address Line 1', 'City', 'State', 'Postal Code', 'Country'] },
    { title: 'Description', optional: true },
    { title: 'Contacts at this organization', emptyText: 'No linked contacts yet.' },
  ],
});

export default { list, detail };
