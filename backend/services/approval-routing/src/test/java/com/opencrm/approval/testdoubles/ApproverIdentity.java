package com.opencrm.approval.testdoubles;

/**
 * Authenticated approver. ADR-016 decision 6: the approver is the caller, never a field on the request.
 * Feature re-points this at {@code CurrentActor}.
 */
public interface ApproverIdentity {

    String currentApproverId();
}
