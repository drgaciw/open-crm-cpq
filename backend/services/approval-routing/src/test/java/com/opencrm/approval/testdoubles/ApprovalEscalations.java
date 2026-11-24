package com.opencrm.approval.testdoubles;

import java.time.Clock;

/**
 * DRG-444 Escalate. The clock is injected so the SLA check does not sleep.
 * Suggested production type: {@code com.opencrm.approval.service.ApprovalEscalationService}.
 * This interface is test-only and is not that FQCN.
 */
public interface ApprovalEscalations {

    Escalation escalate(long approvalId, Clock clock);
}
