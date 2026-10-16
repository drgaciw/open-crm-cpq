package com.opencrm.contract.domain.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only document event. The database trigger rejects UPDATE and DELETE.
 * Hash-chain writes and outbox publication are DRG-580.
 */
@Entity
@Table(name = "amendment_document_events", uniqueConstraints = {
        @UniqueConstraint(name = "uq_amendment_event_seq", columnNames = {"document_id", "seq_no"}),
        @UniqueConstraint(name = "uq_amendment_event_content_hash", columnNames = "content_hash")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmendmentDocumentEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "seq_no", nullable = false)
    private Long seqNo;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 40)
    private AmendmentDocumentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 40)
    private AmendmentDocumentStatus toStatus;

    @Column(name = "revision_no")
    private Integer revisionNo;

    @Column(name = "unit_no")
    private Integer unitNo;

    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 40)
    private ActorType actorType;

    @Column(name = "actor_role", nullable = false, length = 255)
    private String actorRole;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "reason_code", length = 128)
    private String reasonCode;

    @Column(name = "payload_digest", nullable = false, length = 64)
    private String payloadDigest;

    @Column(name = "payload_ref", nullable = false, length = 1024)
    private String payloadRef;

    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "correlation_id", nullable = false, length = 255)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;

    @PrePersist
    void assignIdentity() {
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (recordedAt == null) {
            recordedAt = createdAt;
        }
    }
}
