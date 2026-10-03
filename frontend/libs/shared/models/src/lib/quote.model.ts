/**
 * Quote domain models.
 * Aligned to quote-service (`com.opencrm.quote.*`).
 *
 * QuoteDealType is distinct from CRM DealType (`deal.model.ts`).
 * QuoteApprovalState is distinct from pricing approval strings on PricingResult.
 */

/** Mirrors: com.opencrm.quote.quote.QuoteStatus */
export type QuoteStatus =
  | 'DRAFT'
  | 'IN_REVIEW'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'SENT'
  | 'ACCEPTED'
  | 'EXPIRED'
  | 'CANCELLED'
  | 'REJECTED';

export const QUOTE_STATUS_LABELS: Record<QuoteStatus, string> = {
  DRAFT: 'Draft',
  IN_REVIEW: 'In Review',
  PENDING_APPROVAL: 'Pending Approval',
  APPROVED: 'Approved',
  SENT: 'Sent',
  ACCEPTED: 'Accepted',
  EXPIRED: 'Expired',
  CANCELLED: 'Cancelled',
  REJECTED: 'Rejected',
};

export const QUOTE_STATUS_COLORS: Record<QuoteStatus, string> = {
  DRAFT: '#9e9e9e',
  IN_REVIEW: '#2196f3',
  PENDING_APPROVAL: '#ff9800',
  APPROVED: '#4caf50',
  SENT: '#00bcd4',
  ACCEPTED: '#8bc34a',
  EXPIRED: '#795548',
  CANCELLED: '#757575',
  REJECTED: '#f44336',
};

export const QUOTE_STATUSES: readonly QuoteStatus[] = [
  'DRAFT',
  'IN_REVIEW',
  'PENDING_APPROVAL',
  'APPROVED',
  'SENT',
  'ACCEPTED',
  'EXPIRED',
  'CANCELLED',
  'REJECTED',
];

export function isQuoteStatus(value: unknown): value is QuoteStatus {
  return typeof value === 'string' && (QUOTE_STATUSES as readonly string[]).includes(value);
}

/**
 * Mirrors: com.opencrm.quote.quote.DealType
 * NOT the same as CRM DealType (NEW_BUSINESS, RENEWAL, …).
 */
export type QuoteDealType = 'NEW' | 'AMENDMENT' | 'RENEWAL' | 'EXPANSION';

export const QUOTE_DEAL_TYPE_LABELS: Record<QuoteDealType, string> = {
  NEW: 'New',
  AMENDMENT: 'Amendment',
  RENEWAL: 'Renewal',
  EXPANSION: 'Expansion',
};

/** Mirrors: com.opencrm.quote.quote.ApprovalState */
export type QuoteApprovalState = 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED';

export const QUOTE_APPROVAL_STATE_LABELS: Record<QuoteApprovalState, string> = {
  NONE: 'None',
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
};

/** Mirrors: com.opencrm.quote.lineitem.LineItemType */
export type LineItemType =
  | 'PRODUCT'
  | 'SERVICE'
  | 'SUBSCRIPTION'
  | 'DISCOUNT'
  | 'FEE'
  | 'TAX';

export const LINE_ITEM_TYPE_LABELS: Record<LineItemType, string> = {
  PRODUCT: 'Product',
  SERVICE: 'Service',
  SUBSCRIPTION: 'Subscription',
  DISCOUNT: 'Discount',
  FEE: 'Fee',
  TAX: 'Tax',
};

/**
 * Mirrors RampPriceBasis. Exactly one of unitPrice / discountFraction is set on a step.
 * The type does not enforce that (ADR-011: validation stays in the service and the editor).
 */
export const RAMP_PRICE_BASES = ['ABSOLUTE', 'DISCOUNT_FRACTION'] as const;
export type RampPriceBasis = (typeof RAMP_PRICE_BASES)[number];

export const RAMP_PRICE_BASIS_LABELS: Record<RampPriceBasis, string> = {
  ABSOLUTE: 'Absolute unit price',
  DISCOUNT_FRACTION: 'Discount fraction off list',
};

/** Mirrors RampEscalator.mode. PERCENT amount is a fraction (0.05 = 5%). */
export const RAMP_ESCALATOR_MODES = ['PERCENT', 'AMOUNT'] as const;
export type RampEscalatorMode = (typeof RAMP_ESCALATOR_MODES)[number];

export const RAMP_ESCALATOR_MODE_LABELS: Record<RampEscalatorMode, string> = {
  PERCENT: 'Percent',
  AMOUNT: 'Amount',
};

/**
 * One explicit ramp period. Field names match quote-service RampStep / libs/common RampStep.
 * quantity is a whole number. priceBasis selects which price field is meaningful.
 */
export interface RampStep {
  sequence: number;
  startMonth: number;
  months: number;
  quantity: number;
  priceBasis: RampPriceBasis;
  unitPrice?: number;
  discountFraction?: number;
}

/**
 * Authoring generator only. Expansion is POST .../ramp-schedule/expand.
 * The server stores the expanded steps; these inputs are display-only.
 */
export interface RampEscalator {
  startPrice: number;
  mode: RampEscalatorMode;
  amount: number;
  everyMonths: number;
}

/** Line write body: steps, plus the escalator when the user generated or kept one. */
export interface RampSchedule {
  steps: RampStep[];
  escalator?: RampEscalator;
}

/**
 * POST /api/v1/quotes/{quoteId}/ramp-schedule/expand
 * Does not persist. durationInMonths is the quote term.
 */
export interface RampScheduleExpandRequest {
  durationInMonths: number;
  escalator: RampEscalator;
}

/**
 * Per-period price result. Present on the line only when the quote payload includes it.
 * netUnitPrice / netMonthly / periodAmount are the preview columns.
 */
export interface RampPricedPeriod {
  sequence: number;
  startMonth?: number;
  months: number;
  quantity: number;
  netUnitPrice?: number;
  netMonthly?: number;
  periodAmount?: number;
}

/**
 * Mirrors: com.opencrm.quote.lineitem.QuoteLineItemDto
 * Nested under QuoteDto.lineItems; also returned by line-item endpoints.
 *
 * productType is a free string on the wire (may hold catalog or pricing codes).
 */
export interface QuoteLineItemDto {
  id: number;
  uuid: string;
  productExternalId: string;
  productName?: string;
  productType?: string;
  lineItemType?: string;
  quantity: number;
  unitOfMeasure?: string;
  listPrice?: number;
  unitPrice?: number;
  discountRate?: number;
  discountAmount?: number;
  netPrice?: number;
  extendedAmount?: number;
  billingFrequency?: string;
  sortOrder: number;
  pricingExplanationCode?: string;
  notes?: string;
  /** Absent means the line has no schedule and prices as one implicit period. */
  schedule?: RampSchedule;
  /** Filled when calculate returns period results. Preview-only. */
  pricedPeriods?: RampPricedPeriod[];
  createdAt: string;
  updatedAt: string;
  version?: number;
}

/**
 * Mirrors: com.opencrm.quote.lineitem.LineItemCreateRequest
 * Sent to POST /api/v1/quotes/:id/line-items
 */
export interface LineItemCreateRequest {
  productExternalId: string; // @NotBlank
  productName?: string;
  productType?: string;
  lineItemType?: string;
  quantity?: number; // min 1, default 1
  unitOfMeasure?: string;
  listPrice: number; // @NotNull
  billingFrequency?: string;
  sortOrder?: number;
  notes?: string;
  /** Optional. Absent leaves the line without a schedule. */
  schedule?: RampSchedule;
}

/**
 * Mirrors: com.opencrm.quote.quote.QuoteDto
 * Returned by GET /api/v1/quotes, GET /api/v1/quotes/:id
 */
export interface QuoteDto {
  id: number;
  uuid: string;
  quoteNumber?: string;
  name: string;
  description?: string;
  customerExternalId: string;
  dealExternalId?: string;
  status: QuoteStatus;
  dealType?: QuoteDealType;
  subscriptionType?: string;
  currency?: string;
  region?: string;
  durationInMonths: number;
  startDate?: string;
  endDate?: string;
  subtotal?: number;
  totalDiscount?: number;
  totalTax?: number;
  totalAmount?: number;
  arr?: number;
  tcv?: number;
  acv?: number;
  /** Final-period run rate × 12. Present only when the quote payload includes it. */
  exitArr?: number;
  approvalState?: QuoteApprovalState;
  approvalTier?: string;
  notes?: string;
  lineItems?: QuoteLineItemDto[];
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  version?: number;
}

/**
 * Mirrors: com.opencrm.quote.quote.QuoteCreateRequest
 * Sent to POST /api/v1/quotes
 */
export interface QuoteCreateRequest {
  name: string; // @NotBlank
  description?: string;
  customerExternalId: string; // @NotBlank
  dealExternalId?: string;
  dealType?: QuoteDealType;
  subscriptionType?: string;
  currency?: string; // max 3
  region?: string; // max 20
  durationInMonths?: number;
  startDate?: string;
  endDate?: string;
  notes?: string;
}

/**
 * Mirrors: com.opencrm.quote.quote.QuoteUpdateRequest
 * Sent to PUT /api/v1/quotes/:id
 */
export interface QuoteUpdateRequest {
  name?: string;
  description?: string;
  dealExternalId?: string;
  subscriptionType?: string;
  currency?: string;
  region?: string;
  durationInMonths?: number;
  startDate?: string;
  endDate?: string;
  notes?: string;
}

/**
 * Mirrors: com.opencrm.quote.document.DocumentType
 * Quote PDF generation types (quote-service documents API).
 * Distinct from document-generation microservice DocumentType.
 */
export type DocumentType = 'PROPOSAL' | 'ORDER_FORM' | 'SOW';

export const DOCUMENT_TYPES: readonly DocumentType[] = [
  'PROPOSAL',
  'ORDER_FORM',
  'SOW',
];

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  PROPOSAL: 'Proposal',
  ORDER_FORM: 'Order form',
  SOW: 'Statement of work',
};

export function isDocumentType(value: unknown): value is DocumentType {
  return (
    typeof value === 'string' &&
    (DOCUMENT_TYPES as readonly string[]).includes(value)
  );
}

/**
 * Mirrors: com.opencrm.quote.document.QuoteDocumentDto
 * Returned by POST/GET /api/v1/quotes/:id/documents (metadata only; PDF via download).
 */
export interface QuoteDocumentDto {
  id: number;
  uuid: string;
  quoteId: number;
  documentType: DocumentType;
  fileName?: string;
  contentType?: string;
  fileSize?: number;
  generatedBy?: string;
  createdAt: string;
}
