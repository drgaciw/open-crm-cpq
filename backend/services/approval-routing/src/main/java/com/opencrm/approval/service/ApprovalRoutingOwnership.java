package com.opencrm.approval.service;

/**
 * Approval-routing owns workflow records created from pricing and margin events.
 * Quote-service remains the system of record for quote status transitions only.
 */
public final class ApprovalRoutingOwnership {

    public static final String OWNER = "approval-routing";

    public static final String DISCOUNT_EVENT_SOURCE = "cpq.discount.approval.required";

    private ApprovalRoutingOwnership() {
    }
}
