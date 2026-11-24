package com.opencrm.approval.domain;

/**
 * One row of {@code GetApprovalHistory}. Decision and escalation kinds are written from the
 * events approval-routing already publishes.
 */
public enum ApprovalTimelineKind {
    GRANTED,
    REJECTED,
    ESCALATED,
    COMMENT
}
