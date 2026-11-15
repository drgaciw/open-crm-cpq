import { AssetDto, SubscriptionDto } from '@frontend/shared-models';

/** Vitest fixtures for the read-only Contracts and Assets screens (DRG-761). */
export const MOCK_CONTRACTS: SubscriptionDto[] = [
  {
    id: 7,
    uuid: 'c-7',
    subscriptionNumber: 'SUB-2026-0007',
    customerId: 'ACME-001',
    quoteId: 'Q-20261115-0074',
    status: 'PENDING',
    planName: 'Analytics Suite',
    startDate: '2026-12-01',
    endDate: '2027-11-30',
    renewalDate: '2027-11-30',
    mrr: 1000,
    arr: 12000,
    currencyCode: 'USD',
    erpSyncStatus: 'PENDING',
    createdAt: '2026-11-15T13:00:00Z',
  },
  {
    id: 8,
    uuid: 'c-8',
    subscriptionNumber: 'SUB-2026-0008',
    customerId: 'GLOBEX-002',
    status: 'ACTIVE',
    planName: 'Support Plus',
    startDate: '2026-01-01',
    endDate: '2026-12-31',
    currencyCode: 'EUR',
  },
];

export const MOCK_ASSETS: AssetDto[] = [
  {
    id: 31,
    uuid: 'a-31',
    accountId: 'ACME-001',
    subscriptionId: 7,
    productRef: 'ANALYTICS-PRO',
    quantity: 25,
    status: 'ACTIVE',
    effectiveStart: '2026-12-01',
    effectiveEnd: '2027-11-30',
  },
];
