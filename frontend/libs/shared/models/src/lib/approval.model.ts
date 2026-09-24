/**
 * Quote approval BFF models.
 * Aligned to quote-service (`com.opencrm.quote.approval.*`).
 * FE must call `/api/v1/quotes/approvals/**` — not approval-routing `/api/v1/approvals/**`.
 */

/** Mirrors: com.opencrm.quote.approval.ApprovalTier */
export type ApprovalTier = 'DEAL_DESK' | 'SALES_VP' | 'CFO';

export const APPROVAL_TIERS: readonly ApprovalTier[] = [
  'DEAL_DESK',
  'SALES_VP',
  'CFO',
];

export const APPROVAL_TIER_LABELS: Record<ApprovalTier, string> = {
  DEAL_DESK: 'Deal Desk',
  SALES_VP: 'Sales VP',
  CFO: 'CFO',
};

export function isApprovalTier(value: unknown): value is ApprovalTier {
  return (
    typeof value === 'string' &&
    (APPROVAL_TIERS as readonly string[]).includes(value)
  );
}

/**
 * Mirrors: com.opencrm.quote.approval.ApprovalStatus
 * Named ApprovalRequestStatus to avoid clashing with quote ApprovalState.
 */
export type ApprovalRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export const APPROVAL_REQUEST_STATUSES: readonly ApprovalRequestStatus[] = [
  'PENDING',
  'APPROVED',
  'REJECTED',
];

export const APPROVAL_REQUEST_STATUS_LABELS: Record<
  ApprovalRequestStatus,
  string
> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
};

export function isApprovalRequestStatus(
  value: unknown,
): value is ApprovalRequestStatus {
  return (
    typeof value === 'string' &&
    (APPROVAL_REQUEST_STATUSES as readonly string[]).includes(value)
  );
}

/**
 * Mirrors: com.opencrm.quote.approval.ApprovalRequestDto
 * Returned by GET /api/v1/quotes/approvals/pending and approve/reject POSTs.
 */
export interface ApprovalRequestDto {
  id: number;
  uuid?: string;
  quoteId: number;
  tier: ApprovalTier;
  status: ApprovalRequestStatus;
  requestedBy?: string;
  approver?: string;
  comments?: string;
  requestedAt?: string;
  respondedAt?: string;
  createdAt?: string;
}

/**
 * Mirrors: com.opencrm.quote.approval.ApprovalResponseRequest
 * Body for POST .../approvals/{id}/approve|reject. There is no approver field: the
 * gateway session's user is the approver (ADR-016 decision 6).
 */
export interface ApprovalResponseRequest {
  comments?: string;
}
