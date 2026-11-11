package com.opencrm.audit.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * API-safe projection of an {@link com.opencrm.audit.AuditEntry}. Never the JPA entity itself (ADR-006): this is
 * what {@code AuditTrailService} hands back from {@code append}/{@code search}, and what crm-service's
 * {@code AuditTrailController} returns for SearchAuditTrail.
 */
@Value
@Builder
public class AuditEntryDto {
    Long id;
    String tenantId;
    String aggregateType;
    String aggregateId;
    String principal;
    String action;
    String details;
    Instant occurredAt;
    Instant recordedAt;
    String previousHash;
    String entryHash;
}
