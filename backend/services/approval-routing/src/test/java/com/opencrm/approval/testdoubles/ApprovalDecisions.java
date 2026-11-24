package com.opencrm.approval.testdoubles;

/**
 * DRG-444 Approve and Reject. Both take comments.
 * Suggested production type: {@code com.opencrm.approval.service.ApprovalDecisionService}.
 * This interface is test-only and is not that FQCN.
 */
public interface ApprovalDecisions {

    ApprovalDecision approve(long approvalId, String comments);

    ApprovalDecision reject(long approvalId, String comments);
}
