package com.opencrm.approval.testdoubles;

import java.time.Clock;
import java.util.Objects;

/**
 * Not-implemented Escalate. Reads the clock and the request, then fires immediately and stays on
 * {@link EscalationPath#current()}. The SLA deadline is not consulted, the path is not advanced,
 * and {@code ApprovalEscalated} is not emitted.
 */
public final class NotImplementedApprovalEscalations implements ApprovalEscalations {

    private final ApprovalRequestStore store;
    private final EscalationPath path;

    public NotImplementedApprovalEscalations(
            ApprovalRequestStore store, EscalationPath path, SlaWindow sla, ApprovalEventLog events) {
        this.store = Objects.requireNonNull(store);
        this.path = Objects.requireNonNull(path);
        Objects.requireNonNull(sla);
        Objects.requireNonNull(events);
    }

    @Override
    public Escalation escalate(long approvalId, Clock clock) {
        store.find(approvalId).orElseThrow();
        Objects.requireNonNull(clock.instant(), "now");
        return new Escalation(true, path.current());
    }
}
