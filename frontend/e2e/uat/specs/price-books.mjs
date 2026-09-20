/**
 * Price books — list, "New Price Book" dialog, detail, "Edit Price Book" dialog, entry dialog.
 * Source: frontend/libs/cpq/price-books/src/lib/**
 *
 * The two `<input type="date">` fields are deliberately absent from `fields`: a native date input
 * has no ARIA role, so the field driver's role lookup (`date -> textbox`) cannot find it. They are
 * covered by hand-written checks in the suite (see harness change requests).
 */
import { validateSpec } from '../contract.mjs';

const text = (label, apiPath, extra = {}) => ({
  label, kind: 'input', normalize: 'trim', persist: { create: true, update: true, apiPath }, ...extra,
});

/** Fields shared by the create and edit dialogs (same template, different title/submit). */
const bookFields = () => [
  text('Name', 'name', {
    required: true,
    sample: 'UAT price book',
    validators: [{ kind: 'required', sample: '', expectError: 'Name is required' }],
  }),
  text('Region', 'region', { placeholder: 'e.g. NA' }),
  text('Currency', 'currency', { placeholder: 'USD', normalize: 'upper', sample: 'eur', editSample: 'gbp' }),
  { label: 'Default price book', kind: 'checkbox', default: false, persist: { create: true, update: true, apiPath: 'isDefault' } },
];

/** Native date inputs of the book dialogs — driven by label, not by the field driver. */
export const DATE_FIELDS = ['Effective date', 'Expiration date'];

export const list = validateSpec({
  id: 'price-books',
  path: '/price-books',
  heading: 'Price Books',
  subtitle: 'Browse price books from the pricebook-service API',
  backend: 'pricebook',
  sidebar: 'Price Books',
  actions: [{ name: 'New Price Book', in: 'header', opensDialog: 'New Price Book' }],
  tables: [{
    columns: ['Name', 'Region', 'Currency', 'Default', 'Effective', 'Expires'],
    sortable: ['Name', 'Region', 'Currency', 'Default', 'Effective', 'Expires'],
    pageSizes: [10, 25, 50, 100],
  }],
  emptyState: { title: 'No price books yet', message: 'Create a price book to get started.', action: 'New Price Book' },
  forms: [{
    id: 'create',
    scope: { kind: 'dialog', title: 'New Price Book' },
    flavor: 'reactive',
    open: { action: 'New Price Book' },
    submit: { name: 'Create' },
    cancel: { name: 'Cancel' },
    serverError: 'both',
    fields: bookFields(),
  }],
});

export const editForm = {
  id: 'edit',
  scope: { kind: 'dialog', title: 'Edit Price Book' },
  flavor: 'reactive',
  open: { action: 'Edit' },
  submit: { name: 'Save' },
  cancel: { name: 'Cancel' },
  serverError: 'both',
  fields: bookFields(),
};

const DETAIL_LABELS = ['Region', 'Currency', 'Default', 'Effective', 'Expires', 'Updated', 'Status'];

/** Detail page of one concrete book — built per record because the entry dialog opens from it. */
export const detailFor = (id) => validateSpec({
  id: 'price-books.detail',
  path: `/price-books/${id}`,
  heading: /.+/,
  backend: 'pricebook',
  actions: [
    { name: 'Edit', in: 'header', css: '.edit-book-btn', opensDialog: 'Edit Price Book', enabledWhen: ['draft'] },
    {
      name: 'Delete', in: 'header', css: '.delete-book-btn', enabledWhen: ['draft'],
      confirm: { kind: 'mat', title: 'Delete price book', message: /^Soft-delete ".+"\? It will be removed from the active price book list\.$/m, confirmText: 'Delete', cancelText: 'Cancel' },
      effect: { navigate: '/price-books' },
    },
    { name: 'Back to price books', in: 'header', navigatesTo: '/price-books' },
    { name: 'Add entry', in: 'card', css: '.add-entry-btn', opensDialog: 'Add entry', enabledWhen: ['draft'] },
  ],
  cards: [{ title: 'Price book details', labels: DETAIL_LABELS }],
  // Every detail load fires GET …/entries, which currently answers 500 (H-CPQ-PR-013).
  expectedErrors: ['/entries'],
  forms: [{
    id: 'entry',
    scope: { kind: 'dialog', title: 'Add entry' },
    flavor: 'reactive',
    open: { action: 'Add entry' },
    submit: { name: 'Create' },
    cancel: { name: 'Cancel' },
    serverError: 'both',
    fields: [
      text('Product external ID', 'productExternalId', {
        required: true, placeholder: 'e.g. CRM-SEAT-NAMED', sample: 'UAT-PRODUCT-1',
        validators: [{ kind: 'required', sample: '', expectError: 'Product external ID is required' }],
      }),
      {
        label: 'List price', kind: 'number', required: true, sample: '100', editSample: '120', normalize: 'number',
        persist: { create: true, update: true, apiPath: 'listPrice' },
        validators: [{ kind: 'required', sample: '', expectError: 'List price is required' }],
      },
      { label: 'Floor price (optional)', kind: 'number', sample: '80', normalize: 'number', persist: { create: true, update: true, apiPath: 'floorPrice' } },
      text('Currency (optional)', 'currency', { placeholder: 'USD', normalize: 'upper' }),
    ],
  }],
});

/** Entries table headers (draft book; a published book has no Actions column). */
export const ENTRY_COLUMNS = ['Product', 'List price', 'Floor price', 'Currency', 'Actions'];

export const PUBLISHED_NOTE =
  'Published price books are immutable. Header edit/delete and entry create, edit, and delete are disabled.';

export default { list, editForm, detailFor };
