package com.opencrm.approval.testdoubles;

import java.util.Objects;

/**
 * Not-implemented Approve and Reject. Loads the request and reads the approver, then returns the row
 * unchanged: comments and approver are not recorded, status stays {@code PENDING}, and no
 * {@code ApprovalGranted} or {@code ApprovalRejected} event is emitted.
 */
public final class NotImplementedApprovalDecisions implements ApprovalDecisions {

    private final ApprovalRequestStore store;
    private final ApproverIdentity approver;

    public NotImplementedApprovalDecisions(
            ApprovalRequestStore store, ApproverIdentity approver, ApprovalEventLog events) {
        this.store = Objects.requireNonNull(store);
        this.approver = Objects.requireNonNull(approver);
        Objects.requireNonNull(events);
    }

    @Override
    public ApprovalDecision approve(long approvalId, String comments) {
        return leftUndecided(approvalId);
    }

    @Override
    public ApprovalDecision reject(long approvalId, String comments) {
        return leftUndecided(approvalId);
    }

    private ApprovalDecision leftUndecided(long approvalId) {
        PendingApproval current = store.find(approvalId).orElseThrow();
        approver.currentApproverId();
        return new ApprovalDecision(current.id(), current.status(), null, null, current.quoteId());
    }
}
