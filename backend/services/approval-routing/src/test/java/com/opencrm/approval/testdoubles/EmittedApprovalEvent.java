package com.opencrm.approval.testdoubles;

/**
 * One emitted decision event. {@code name} is {@code ApprovalGranted}, {@code ApprovalRejected},
 * or {@code ApprovalEscalated}. {@code QuoteApprovalRejected} does not satisfy these specs.
 */
public record EmittedApprovalEvent(
        String name,
        long approvalId,
        String quoteId,
        String actorId,
        String comments) {
}
