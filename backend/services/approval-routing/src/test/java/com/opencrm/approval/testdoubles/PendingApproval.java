package com.opencrm.approval.testdoubles;

/**
 * Approval row the DRG-444 decision and escalation specs load.
 * Feature replaces this with the approval-routing request model.
 */
public record PendingApproval(long id, String quoteId, String status, String assigneeId) {
}
