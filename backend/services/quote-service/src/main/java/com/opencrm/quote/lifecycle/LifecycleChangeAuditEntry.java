package com.opencrm.quote.lifecycle;

import java.util.UUID;

/**
 * One rejection row. {@code lifecycleChangeId} is null when create stores no resource (AC-S5-6).
 * There is no quote id: {@code commercial_decision_audit.quote_id} is NOT NULL.
 */
public record LifecycleChangeAuditEntry(
        String tid,
        UUID lifecycleChangeId,
        String subscriptionId,
        String decisionKind,
        String reasonCode,
        String decidedBy,
        String inputsJson,
        String outputsJson) {
}
