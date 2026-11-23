/**
 * Asset / installed-base models.
 * Mirrors: com.opencrm.contract.asset.* (contract-asset-management OHS).
 *
 * Read in the SPA by @frontend/cpq-contracts (/assets and contract detail, DRG-761); also keeps
 * OHS/report clients and renewal-from-assets continuation payloads typed.
 */

/** Mirrors: com.opencrm.contract.asset.AssetStatus */
export type AssetStatus = 'ACTIVE' | 'AMENDED' | 'SUPERSEDED' | 'TERMINATED';

export const ASSET_STATUSES: readonly AssetStatus[] = [
  'ACTIVE',
  'AMENDED',
  'SUPERSEDED',
  'TERMINATED',
] as const;

export const ASSET_STATUS_LABELS: Record<AssetStatus, string> = {
  ACTIVE: 'Active',
  AMENDED: 'Amended',
  SUPERSEDED: 'Superseded',
  TERMINATED: 'Terminated',
};

export function isAssetStatus(value: unknown): value is AssetStatus {
  return typeof value === 'string' && (ASSET_STATUSES as readonly string[]).includes(value);
}

/** Mirrors: com.opencrm.contract.asset.AssetChurnKind */
export type AssetChurnKind = 'TERMINATED' | 'DOWNSIZED';

export const ASSET_CHURN_KINDS: readonly AssetChurnKind[] = ['TERMINATED', 'DOWNSIZED'] as const;

export function isAssetChurnKind(value: unknown): value is AssetChurnKind {
  return typeof value === 'string' && (ASSET_CHURN_KINDS as readonly string[]).includes(value);
}

/**
 * Mirrors: com.opencrm.contract.asset.AssetDto
 * Returned by GET /api/v1/assets, GET /api/v1/assets/:id
 */
export interface AssetDto {
  id: number;
  uuid: string;
  accountId: string;
  contractLineId?: string;
  subscriptionId?: number;
  entitlementId?: string | null;
  productRef?: string | null;
  quantity?: number | null;
  status: AssetStatus;
  effectiveStart?: string;
  effectiveEnd?: string;
  supersededBy?: string | null;
  supersedes?: string | null;
  createdAt?: string;
  updatedAt?: string | null;
}

/**
 * Mirrors: com.opencrm.contract.asset.InstalledAssetDto
 * Returned by GET /api/v1/assets?accountId=&asOf= (installed base as-of)
 */
export interface InstalledAssetDto {
  assetId: string;
  accountId: string;
  contractLineId?: string | null;
  subscriptionId?: number | null;
  productRef?: string | null;
  quantity?: number | null;
  effectiveStart?: string;
  effectiveEnd?: string | null;
  status: AssetStatus;
}

/**
 * Mirrors: com.opencrm.contract.asset.AssetHistoryEntryDto
 * Returned by GET /api/v1/assets/:id/history
 */
export interface AssetHistoryEntryDto {
  assetId: string;
  accountId: string;
  eventType?: string;
  effectiveDate?: string;
  effectiveEnd?: string | null;
  sourceAmendmentId?: string | null;
  sourceApplyUnit?: string | null;
  predecessorAssetId?: string | null;
  successorAssetId?: string | null;
  contractLineId?: string | null;
  subscriptionId?: number | null;
  productRef?: string | null;
  quantity?: number | null;
}

/**
 * Mirrors: com.opencrm.contract.asset.UpcomingRenewalsReport
 * GET /api/v1/assets/reports/upcoming-renewals
 */
export interface UpcomingRenewalsReport {
  accountId: string;
  windowStart: string;
  windowEnd: string;
  assetCount: number;
  assets: InstalledAssetDto[];
}

/**
 * Mirrors: com.opencrm.contract.asset.AssetChurnEventDto
 */
export interface AssetChurnEventDto {
  assetId: string;
  accountId: string;
  churnKind: AssetChurnKind;
  effectiveDate: string;
  quantityBefore?: number | null;
  quantityAfter?: number;
  quantityChurned?: number | null;
  productRef?: string | null;
}

/**
 * Mirrors: com.opencrm.contract.asset.AssetChurnReport
 * GET /api/v1/assets/reports/churn
 */
export interface AssetChurnReport {
  accountId: string;
  periodStart: string;
  periodEnd: string;
  assetCount: number;
  eventCount: number;
  quantityChurned?: number;
  events: AssetChurnEventDto[];
}
