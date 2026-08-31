import { PricingCustomerDto } from '@frontend/shared-models';

/** Vitest fixtures only — not used at runtime. */
export const MOCK_PRICING_CUSTOMERS: PricingCustomerDto[] = [
  {
    id: 1,
    uuid: 'pc-uuid-1',
    externalId: 'CUST-ACME',
    name: 'Acme Corp',
    type: 'ACCOUNT',
    segment: 'ENTERPRISE',
    region: 'NA',
    industry: 'Technology',
    churnScore: 12,
    annualRevenue: 50_000_000,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-15T00:00:00Z',
  },
  {
    id: 2,
    uuid: 'pc-uuid-2',
    externalId: 'CUST-BETA',
    name: 'Beta SMB',
    type: 'ACCOUNT',
    segment: 'SMB',
    region: 'EMEA',
    industry: 'Retail',
    churnScore: 35,
    annualRevenue: 2_000_000,
    createdAt: '2026-02-01T00:00:00Z',
    updatedAt: '2026-02-10T00:00:00Z',
  },
  {
    id: 3,
    uuid: 'pc-uuid-3',
    externalId: 'CUST-GAMMA',
    name: 'Gamma Startup',
    type: 'PROSPECT',
    segment: 'STARTUP',
    region: 'APAC',
    industry: 'SaaS',
    createdAt: '2026-03-01T00:00:00Z',
    updatedAt: '2026-03-01T00:00:00Z',
  },
];

export function findMockPricingCustomer(
  id: number,
): PricingCustomerDto | undefined {
  return MOCK_PRICING_CUSTOMERS.find((c) => c.id === id);
}
