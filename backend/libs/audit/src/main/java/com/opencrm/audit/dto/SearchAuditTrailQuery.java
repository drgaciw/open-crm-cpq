package com.opencrm.audit.dto;

import java.time.Instant;

/**
 * SearchAuditTrail filter criteria (DRG-309): tenant, aggregate (type and/or id), principal and an occurred-at
 * time range ({@code from}/{@code to}, both inclusive). Every field is optional; a null field matches any value.
 */
public record SearchAuditTrailQuery(
        String tenantId,
        String aggregateType,
        String aggregateId,
        String principal,
        Instant from,
        Instant to) {
}
