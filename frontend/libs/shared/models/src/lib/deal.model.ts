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

export const STAGE_COLORS: Record<string, string> = {
  PROSPECTING: '#2196f3',
  QUALIFICATION: '#ff9800',
  NEEDS_ANALYSIS: '#00bcd4',
  PROPOSAL: '#9c27b0',
  NEGOTIATION: '#f44336',
  WON: '#4caf50',
  LOST: '#757575',
};

export const STAGE_LABELS: Record<string, string> = {
  PROSPECTING: 'Prospecting',
  QUALIFICATION: 'Qualification',
  NEEDS_ANALYSIS: 'Needs Analysis',
  PROPOSAL: 'Proposal',
  NEGOTIATION: 'Negotiation',
  WON: 'Won',
  LOST: 'Lost',
};

export const STATUS_COLORS: Record<string, string> = {
  NEW: '#2196f3',
  CONTACTED: '#ff9800',
  QUALIFIED: '#4caf50',
  CONVERTED: '#9c27b0',
  LOST: '#757575',
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
  probability?: number;
  expectedCloseDate?: string;
  actualCloseDate?: string;
  description?: string;
  ownerId?: string;
  sourceLeadId?: number;
  type?: DealType;
  lostReason?: string;
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
  name: string; // @NotBlank
  organizationId?: number;
  primaryContactId?: number;
  amount?: number; // @PositiveOrZero
  currency?: string; // defaults to "USD"
  expectedCloseDate?: string;
  description?: string;
  ownerId?: string;
  type?: DealType; // defaults to NEW_BUSINESS
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
  reason: string; // @NotBlank
}

/**
 * Mirrors: com.opencrm.crm.deal.PipelineSummaryDto
 * The backend returns List<PipelineSummaryDto> from GET /api/v1/deals/pipeline
 * Each entry represents one stage's summary.
 */
export interface PipelineSummaryDto {
  stage: DealStage;
  count: number;
  totalAmount: number;
  weightedAmount: number;
}
