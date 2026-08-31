import { PriceBookDto, PriceBookEntryDto } from '@frontend/shared-models';

/** Vitest fixtures only — not used at runtime. */
export const MOCK_PRICE_BOOKS: PriceBookDto[] = [
  {
    id: 1,
    uuid: 'pb-uuid-1',
    name: 'North America Standard',
    region: 'NA',
    currency: 'USD',
    isDefault: true,
    effectiveDate: '2026-01-01',
    expirationDate: '2026-12-31',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-15T00:00:00Z',
  },
  {
    id: 2,
    uuid: 'pb-uuid-2',
    name: 'EMEA Enterprise',
    region: 'EMEA',
    currency: 'EUR',
    isDefault: false,
    effectiveDate: '2026-03-01',
    createdAt: '2026-02-01T00:00:00Z',
    updatedAt: '2026-02-10T00:00:00Z',
  },
  {
    id: 3,
    uuid: 'pb-uuid-3',
    name: 'APAC Promo',
    region: 'APAC',
    currency: 'USD',
    isDefault: false,
    createdAt: '2026-04-01T00:00:00Z',
    updatedAt: '2026-04-01T00:00:00Z',
  },
];

export const MOCK_PRICE_BOOK_ENTRIES: PriceBookEntryDto[] = [
  {
    id: 11,
    uuid: 'pbe-uuid-11',
    priceBookId: 1,
    productExternalId: 'CRM-SEAT-NAMED',
    listPrice: 120,
    floorPrice: 90,
    currency: 'USD',
    createdAt: '2026-01-02T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
  },
  {
    id: 12,
    uuid: 'pbe-uuid-12',
    priceBookId: 1,
    productExternalId: 'CPQ-FLAT',
    listPrice: 299,
    floorPrice: 250,
    currency: 'USD',
    createdAt: '2026-01-03T00:00:00Z',
    updatedAt: '2026-01-03T00:00:00Z',
  },
];

export function findMockPriceBook(id: number): PriceBookDto | undefined {
  return MOCK_PRICE_BOOKS.find((b) => b.id === id);
}
