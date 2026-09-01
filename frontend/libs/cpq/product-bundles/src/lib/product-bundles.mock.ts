import { ProductBundleDto, BundleItemDto } from './product-bundle.model';

/** Vitest fixtures only — not used at runtime. */
export const MOCK_BUNDLE_ITEMS: BundleItemDto[] = [
  {
    id: 101,
    uuid: 'bi-uuid-101',
    productId: 20,
    quantity: 1,
    optional: false,
    sortOrder: 0,
  },
  {
    id: 102,
    uuid: 'bi-uuid-102',
    productId: 21,
    quantity: 2,
    optional: true,
    sortOrder: 1,
  },
];

export const MOCK_PRODUCT_BUNDLES: ProductBundleDto[] = [
  {
    id: 1,
    uuid: 'pbundle-uuid-1',
    productId: 10,
    name: 'Starter Pack',
    description: 'Entry bundle',
    discountType: 'PERCENTAGE',
    discountValue: 10,
    minItems: 1,
    maxItems: 5,
    status: 'ACTIVE',
    items: [MOCK_BUNDLE_ITEMS[0]],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-15T00:00:00Z',
  },
  {
    id: 2,
    uuid: 'pbundle-uuid-2',
    productId: 11,
    name: 'Enterprise Suite',
    description: 'Full suite',
    discountType: 'FIXED_AMOUNT',
    discountValue: 250,
    minItems: 2,
    maxItems: 10,
    status: 'ACTIVE',
    items: [...MOCK_BUNDLE_ITEMS],
    createdAt: '2026-02-01T00:00:00Z',
    updatedAt: '2026-02-10T00:00:00Z',
  },
  {
    id: 3,
    uuid: 'pbundle-uuid-3',
    productId: 10,
    name: 'Empty Draft Bundle',
    discountType: 'NONE',
    minItems: 0,
    maxItems: 3,
    status: 'DRAFT',
    items: [],
    createdAt: '2026-03-01T00:00:00Z',
    updatedAt: '2026-03-01T00:00:00Z',
  },
];

export function findMockProductBundle(id: number): ProductBundleDto | undefined {
  return MOCK_PRODUCT_BUNDLES.find((b) => b.id === id);
}
