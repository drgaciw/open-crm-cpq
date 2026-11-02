/**
 * Subscription / contract-commitment models.
 * Mirrors: com.opencrm.contract.dto.SubscriptionDto (contract-asset-management).
 *
 * Browser OHS: GET /api/v1/subscriptions (gateway → CAM). Amendments remain
 * unreleased on the gateway; see amendment.model.ts for typed future UI.
 */

/** Mirrors: com.opencrm.contract.domain.SubscriptionStatus */
export type SubscriptionStatus =
  | 'PENDING'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'CANCELLED'
  | 'EXPIRED';

export const SUBSCRIPTION_STATUSES: readonly SubscriptionStatus[] = [
  'PENDING',
  'ACTIVE',
  'SUSPENDED',
  'CANCELLED',
  'EXPIRED',
] as const;

export const SUBSCRIPTION_STATUS_LABELS: Record<SubscriptionStatus, string> = {
  PENDING: 'Pending',
  ACTIVE: 'Active',
  SUSPENDED: 'Suspended',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
};

export function isSubscriptionStatus(value: unknown): value is SubscriptionStatus {
  return typeof value === 'string' && (SUBSCRIPTION_STATUSES as readonly string[]).includes(value);
}

/**
 * Mirrors: com.opencrm.contract.dto.SubscriptionDto
 * Returned by GET /api/v1/subscriptions, GET /api/v1/subscriptions/:id
 */
export interface SubscriptionDto {
  id: number;
  uuid: string;
  subscriptionNumber?: string;
  customerId?: string;
  quoteId?: string;
  status: SubscriptionStatus;
  planName?: string;
  startDate?: string;
  endDate?: string;
  renewalDate?: string;
  mrr?: number;
  arr?: number;
  currencyCode?: string;
  usageBand?: string;
  erpSyncStatus?: string;
  erpSubscriptionId?: string;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;
  version?: number;
}
