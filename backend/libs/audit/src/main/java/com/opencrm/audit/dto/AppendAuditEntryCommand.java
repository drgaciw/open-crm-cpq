package com.opencrm.audit.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * Request to append one entry to the audit trail (the write side of {@code AuditTrailService}, DRG-309).
 * {@code aggregateType}, {@code aggregateId} and {@code action} are required; everything else is optional.
 *
 * @param occurredAt when the audited action happened; defaults to "now" ({@code AuditTrailService.append}) when
 *                    null, so most callers can omit it.
 */
public record AppendAuditEntryCommand(
        String tenantId,
        String aggregateType,
        String aggregateId,
        String principal,
        String action,
        String details,
        Instant occurredAt) {

    public AppendAuditEntryCommand {
        Objects.requireNonNull(aggregateType, "aggregateType is required");
        Objects.requireNonNull(aggregateId, "aggregateId is required");
        Objects.requireNonNull(action, "action is required");
    }
}
