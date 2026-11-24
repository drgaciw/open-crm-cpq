package com.opencrm.approval.testdoubles;

/**
 * Recorded Approve or Reject. {@code status} is {@code APPROVED} or {@code REJECTED}.
 * Feature maps this from the decision service result.
 */
public record ApprovalDecision(
        long approvalId,
        String status,
        String comments,
        String approverId,
        String quoteId) {
}
