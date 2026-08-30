package com.opencrm.approval.domain;

/**
 * Quote-service BFF approval tiers accepted by {@code POST /api/v1/approvals}.
 * Maps to routing {@code approvalTier} string + {@link ApprovalType}.
 */
public enum QuoteApprovalTier {
    DEAL_DESK,
    SALES_VP,
    CFO
}
