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
