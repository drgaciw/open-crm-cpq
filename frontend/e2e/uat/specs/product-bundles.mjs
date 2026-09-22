/**
 * Product bundles — list, New Product Bundle dialog, detail page, Add bundle item dialog.
 * Source: frontend/libs/cpq/product-bundles/src/lib/**
 */
import { validateSpec } from '../contract.mjs';

export const DISCOUNT_TYPES = ['NONE', 'PERCENTAGE', 'FIXED_AMOUNT'];

export const list = validateSpec({
  id: 'product-bundles',
  path: '/product-bundles',
  heading: 'Product Bundles',
  subtitle: 'Browse and manage product bundles from /api/v1/product-bundles',
  backend: 'product',
  sidebar: 'Bundles',
  actions: [{ name: 'New Bundle', in: 'header', opensDialog: 'New Product Bundle' }],
  tables: [{
    columns: ['Name', 'Product ID', 'Discount', 'Value', 'Items', 'Status'],
    pageSizes: [10, 25, 50, 100],
  }],
  emptyState: {
    title: 'No product bundles yet',
    message: 'Create a product bundle to get started.',
    action: 'New Bundle',
  },
  forms: [{
    id: 'new-bundle',
    scope: { kind: 'dialog', title: 'New Product Bundle' },
    flavor: 'reactive',
    open: { action: 'New Bundle' },
    submit: { name: 'Create' },
    cancel: { name: 'Cancel' },
    serverError: 'banner',
    fields: [
      {
        // sample is replaced by a real product id in the CRUD workflow; here it only has to be valid.
        label: 'Product ID', kind: 'number', required: true, default: '', sample: '1', normalize: 'number',
        persist: { create: true, apiPath: 'productId' },
        validators: [
          { kind: 'required', sample: '', expectError: 'Product ID is required' },
          { kind: 'min', sample: '0', expectError: 'Product ID must be at least 1' },
        ],
      },
      {
        label: 'Name', kind: 'input', required: true, sample: 'UAT bundle', normalize: 'trim',
        persist: { create: true, apiPath: 'name' },
        validators: [{ kind: 'required', sample: '', expectError: 'Name is required' }],
      },
      { label: 'Description (optional)', kind: 'textarea', sample: 'Created by the UAT.', normalize: 'trim',
        persist: { create: true, apiPath: 'description' } },
      { label: 'Discount type', kind: 'select', default: 'NONE', sample: 'PERCENTAGE',
        persist: { create: true, apiPath: 'discountType' },
        options: DISCOUNT_TYPES.map((v) => ({ label: v, value: v })) },
      { label: 'Discount value', kind: 'number', default: '', sample: '12.5', normalize: 'number',
        persist: { create: true, apiPath: 'discountValue' } },
      { label: 'Min items', kind: 'number', default: '', sample: '1', normalize: 'number',
        persist: { create: true, apiPath: 'minItems' } },
      { label: 'Max items', kind: 'number', default: '', sample: '5', normalize: 'number',
        persist: { create: true, apiPath: 'maxItems' } },
    ],
  }],
});

/** "Add bundle item" opens from /product-bundles/:id — wrapped with a concrete path by the suite. */
export const itemDialog = (path) => validateSpec({
  id: 'product-bundles.item',
  path,
  heading: /.+/,
  backend: 'product',
  forms: [{
    id: 'add-item',
    scope: { kind: 'dialog', title: 'Add bundle item' },
    flavor: 'reactive',
    open: { action: 'Add item' },
    submit: { name: 'Add' },
    cancel: { name: 'Cancel' },
    serverError: 'banner',
    fields: [
      {
        label: 'Product ID', kind: 'number', required: true, default: '', sample: '1',
        validators: [
          { kind: 'required', sample: '', expectError: 'Product ID is required' },
          { kind: 'min', sample: '0', expectError: 'Product ID must be at least 1' },
        ],
      },
      { label: 'Quantity', kind: 'number', default: '1',
        validators: [{ kind: 'min', sample: '0', expectError: 'Quantity must be at least 1' }] },
      { label: 'Sort order', kind: 'number', default: '0' },
      { label: 'Optional item', kind: 'checkbox', default: false },
    ],
  }],
});

export const detail = validateSpec({
  id: 'product-bundles.detail',
  path: '/product-bundles/:id',
  heading: /.+/,
  backend: 'product',
  actions: [
    { name: 'Back to bundles', in: 'header', navigatesTo: '/product-bundles' },
    {
      name: 'Delete', in: 'header',
      confirm: {
        kind: 'mat', title: 'Delete bundle',
        message: /Soft-delete ".+"\? It will be removed from the active bundle list\./,
        confirmText: 'Delete', cancelText: 'Cancel',
      },
      effect: { navigate: '/product-bundles' },
    },
  ],
  cards: [
    { title: 'Bundle details', labels: ['Product ID', 'Status', 'Discount type', 'Discount value', 'Min items', 'Max items', 'Description'] },
  ],
});

export default { list, detail };
