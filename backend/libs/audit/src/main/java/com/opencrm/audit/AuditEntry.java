package com.opencrm.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in the append-only, hash-chained audit trail (DRG-309 / BC-20-01).
 *
 * <p>Deliberately does <b>not</b> extend {@code com.opencrm.common.entity.BaseEntity}: that base class carries
 * {@code updatedAt}/{@code updatedBy}, an optimistic-lock {@code version}, and a soft-delete flag — all of which
 * imply an entry can change after it is written. An audit entry never can. Every column here is
 * {@code updatable = false}, so Hibernate never issues an {@code UPDATE} against this table, and there is no
 * {@code deleted} flag: a row is either present, unmodified, or it was never written. {@link AuditEntryRepository}
 * exposes no update/delete method either, and the owning service's Flyway migration adds a database trigger that
 * rejects {@code UPDATE}/{@code DELETE} outright (belt-and-suspenders: JPA convention, repository surface, and the
 * storage layer itself all refuse mutation).
 *
 * <p>Tamper-evidence comes from {@link #previousHash} / {@link #entryHash}: each entry's hash is computed over its
 * own content plus the previous entry's hash (see {@link AuditHashing}), so changing or removing any row breaks the
 * chain for every entry after it. The chain is a single, global, append-only sequence ordered by {@link #id}
 * (not partitioned per tenant or aggregate) — the simplest structure that still makes the whole trail tamper
 * evident, matching "an AuditEntry store" (singular) in the story's acceptance criteria.
 */
@Entity
@Table(name = "audit_entries", indexes = {
        @Index(name = "idx_audit_entries_tenant", columnList = "tenant_id"),
        @Index(name = "idx_audit_entries_aggregate", columnList = "aggregate_type, aggregate_id"),
        @Index(name = "idx_audit_entries_principal", columnList = "principal"),
        @Index(name = "idx_audit_entries_occurred_at", columnList = "occurred_at")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private String uuid;

    /** Entra {@code tid} of the tenant the audited action happened under. Null for a system/global action. */
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;

    /** The kind of aggregate audited, e.g. {@code "Lead"}, {@code "Quote"}. */
    @Column(name = "aggregate_type", nullable = false, updatable = false)
    private String aggregateType;

    /** The audited aggregate's identifier, as a string so any service's id scheme fits. */
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private String aggregateId;

    /** Who did it: {@code user:<oid>} or {@code service:<clientId>} (same source pattern used elsewhere, e.g.
     * {@code account_hierarchy_changes.decided_by}). Null when the actor could not be resolved. */
    @Column(name = "principal", updatable = false)
    private String principal;

    /** What happened, e.g. {@code "CREATE"}, {@code "UPDATE"}, {@code "DELETE"}, or a domain-specific verb. */
    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    /** Free-form details of the audited action (e.g. a JSON diff or description). Opaque to this library. */
    @Column(name = "details", columnDefinition = "TEXT", updatable = false)
    private String details;

    /** When the audited action happened, as reported by the caller. */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** When this entry was actually persisted (server clock) — independent of {@link #occurredAt}. */
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    /** The previous entry's {@link #entryHash}, or {@link AuditHashing#GENESIS_HASH} for the first entry. */
    @Column(name = "previous_hash", nullable = false, length = 64, updatable = false)
    private String previousHash;

    /** SHA-256 (hex) of this entry's own content plus {@link #previousHash}. See {@link AuditHashing}. */
    @Column(name = "entry_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String entryHash;

    @PrePersist
    void assignUuidIfAbsent() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID().toString();
        }
    }
}
