import { CRM_STATUS_TONES } from './status-tokens';

/**
 * Mirrors: com.opencrm.crm.deal.DealStage
 */
export type DealStage =
  | 'PROSPECTING'
  | 'QUALIFICATION'
  | 'NEEDS_ANALYSIS'
  | 'PROPOSAL'
  | 'NEGOTIATION'
  | 'WON'
  | 'LOST';

/**
 * Mirrors: com.opencrm.crm.deal.DealType
 */
export type DealType =
  | 'NEW_BUSINESS'
  | 'EXISTING_BUSINESS'
  | 'RENEWAL'
  | 'UPSELL'
  | 'CROSS_SELL';

/** Solid fills for kanban / pipeline bars — CSS vars from UI-CRM-SLDS tokens. */
export const STAGE_COLORS: Record<string, string> = {
  PROSPECTING: CRM_STATUS_TONES.info.ink,
  QUALIFICATION: CRM_STATUS_TONES.warning.ink,
  NEEDS_ANALYSIS: CRM_STATUS_TONES.teal.ink,
  PROPOSAL: CRM_STATUS_TONES.accent.ink,
  NEGOTIATION: CRM_STATUS_TONES.danger.ink,
  WON: CRM_STATUS_TONES.success.ink,
  LOST: CRM_STATUS_TONES.muted.ink,
};

export const STAGE_LABELS: Record<string, string> = {
  PROSPECTING: 'Prospecting', QUALIFICATION: 'Qualification', NEEDS_ANALYSIS: 'Needs Analysis',
  PROPOSAL: 'Proposal', NEGOTIATION: 'Negotiation', WON: 'Won', LOST: 'Lost',
};

/** Lead / pipeline status solids for dashboard chips — CSS vars from UI-CRM-SLDS tokens. */
export const STATUS_COLORS: Record<string, string> = {
  NEW: CRM_STATUS_TONES.info.ink,
  CONTACTED: CRM_STATUS_TONES.warning.ink,
  QUALIFIED: CRM_STATUS_TONES.success.ink,
  UNQUALIFIED: CRM_STATUS_TONES.warning.ink,
  CONVERTED: CRM_STATUS_TONES.accent.ink,
  LOST: CRM_STATUS_TONES.muted.ink,
};

/**
 * Mirrors: com.opencrm.crm.deal.DealDto
 * Returned by GET /api/v1/deals, GET /api/v1/deals/:id
 *
 * NOTE: Backend returns flat organizationId/organizationName and primaryContactId/primaryContactName,
 * NOT nested DTO objects.
 */
export interface DealDto {
  id: number;
  uuid: string;
  name: string;
  organizationId?: number;
  organizationName?: string;
  primaryContactId?: number;
  primaryContactName?: string;
  stage: DealStage;
  amount?: number;
  currency?: string;
  /** ADR-032: amount frozen in the base currency; absent when not converted. */
  baseAmount?: number;
  baseCurrency?: string;
  baseWeightedAmount?: number;
  fxRate?: number;
  fxRateAsOf?: string;
  fxSource?: string;
  probability?: number;
  expectedCloseDate?: string;
  actualCloseDate?: string;
  description?: string;
  ownerId?: string;
  sourceLeadId?: number;
  type?: DealType;
  lostReason?: string;
  /** Quote accepted for this deal, absent until applied. */
  acceptedQuoteId?: string;
  wonAt?: string;
  lostAt?: string;
  nextStep?: string;
  nextStepDate?: string;
  competitors?: string;
  weightedAmount?: number;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  version?: number;
}

/**
 * Mirrors: com.opencrm.crm.deal.DealCreateRequest
 * Sent to POST /api/v1/deals
 */
export interface DealCreateRequest {
  name: string;                  // @NotBlank
  organizationId?: number;
  primaryContactId?: number;
  amount?: number;               // @PositiveOrZero
  currency?: string;             // defaults to "USD"
  expectedCloseDate?: string;
  description?: string;
  ownerId?: string;
  type?: DealType;               // defaults to NEW_BUSINESS
}

/**
 * Mirrors: com.opencrm.crm.deal.DealUpdateRequest
 * Sent to PUT /api/v1/deals/:id
 */
export interface DealUpdateRequest {
  name?: string;
  organizationId?: number;
  primaryContactId?: number;
  stage?: DealStage;
  amount?: number;
  currency?: string;
  expectedCloseDate?: string;
  description?: string;
  ownerId?: string;
  type?: DealType;
}

/**
 * Mirrors: com.opencrm.crm.deal.DealLoseRequest
 * Sent to POST /api/v1/deals/:id/lose
 */
export interface DealLoseRequest {
  reason: string;                // @NotBlank
}

/**
 * Mirrors: com.opencrm.crm.deal.PipelineSummaryDto
 * The backend returns List<PipelineSummaryDto> from GET /api/v1/deals/pipeline
 * Each entry represents one stage's summary.
 */
export interface PipelineSummaryDto {
  stage: DealStage;
  /** All deals in the stage, converted or not. */
  count: number;
  /** Sum of base amounts in `baseCurrency` (ADR-032). Excludes unconverted deals. */
  totalAmount: number;
  /** Probability-weighted sum of base amounts in `baseCurrency`. */
  weightedAmount: number;
  /** ISO 4217 code for totalAmount / weightedAmount. */
  baseCurrency?: string;
  /** Deals with an amount left out of the totals because no FX rate was available. */
  unconvertedCount?: number;
}

/** Fallback when an older backend omits baseCurrency. */
export const DEFAULT_BASE_CURRENCY = 'USD';
