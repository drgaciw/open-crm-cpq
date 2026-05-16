package com.opencrm.quote.approval;

import java.util.Set;

/**
 * Documents approval ownership boundaries for Stack S2C-6.
 * {@link ApprovalService} persists manual quote workflow requests.
 * Discount-driven approvals are owned by {@code approval-routing} via Kafka consumers.
 */
public final class QuoteApprovalOwnership {

    public static final String OWNER = "quote-service";

    public static final Set<String> ROUTED_VIA_APPROVAL_ROUTING = Set.of("DISCOUNT");

    private QuoteApprovalOwnership() {
    }
}
