package com.opencrm.contract.documents.events;

import com.opencrm.contract.documents.audit.AmendmentAuditOutbox;
import com.opencrm.contract.domain.documents.AmendmentDocument;
import com.opencrm.contract.domain.documents.AmendmentDocumentEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Appends one hash-chained event and its audit outbox row in the caller's transaction (DRG-580).
 * The document row lock serializes concurrent appends: the waiter reads the new max sequence and
 * continues, so the per-document sequence stays gapless. A duplicate {@code source_event_id}
 * returns the original row and does not write a second outbox event.
 */
@Service
public class AmendmentEventWriter {

    private static final Pattern HEX = Pattern.compile("^[0-9a-f]{64}$");

    private final EntityManager entityManager;
    private final AmendmentAuditOutbox auditOutbox;

    public AmendmentEventWriter(EntityManager entityManager, AmendmentAuditOutbox auditOutbox) {
        this.entityManager = entityManager;
        this.auditOutbox = auditOutbox;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AmendmentDocumentEvent append(AmendmentEventAppend command) {
        Objects.requireNonNull(command, "command");
        validate(command);
        AmendmentDocument document = entityManager.find(
                AmendmentDocument.class, command.documentId(), LockModeType.PESSIMISTIC_WRITE);
        if (document == null) {
            throw new IllegalArgumentException("Unknown amendment document " + command.documentId());
        }
        if (command.sourceEventId() != null) {
            AmendmentDocumentEvent existing = findBySource(command.sourceEventId());
            if (existing != null) {
                if (!existing.getDocumentId().equals(document.getId())) {
                    throw new IllegalArgumentException(
                            "source_event_id already belongs to document " + existing.getDocumentId());
                }
                return existing;
            }
        }
        long seqNo = nextSeq(document.getId());
        String prevHash = seqNo == 1 ? AmendmentEventHasher.GENESIS_PREV_HASH : contentHashAt(document.getId(), seqNo - 1);
        Instant createdAt = micros(Instant.now());
        AmendmentDocumentEvent event = AmendmentDocumentEvent.builder()
                .eventId(UUID.randomUUID())
                .tenantId(document.getTenantId())
                .documentId(document.getId())
                .seqNo(seqNo)
                .eventType(command.eventType())
                .fromStatus(command.fromStatus())
                .toStatus(command.toStatus())
                .revisionNo(command.revisionNo())
                .unitNo(command.unitNo())
                .actorId(command.actorId())
                .actorType(command.actorType())
                .actorRole(command.actorRole())
                .occurredAt(micros(command.occurredAt()))
                .recordedAt(createdAt)
                .reasonCode(command.reasonCode())
                .payloadDigest(command.payloadDigest())
                .payloadRef(command.payloadRef())
                .prevHash(prevHash)
                .sourceEventId(command.sourceEventId())
                .correlationId(command.correlationId())
                .createdAt(createdAt)
                .createdBy(command.createdBy())
                .build();
        event.setContentHash(AmendmentEventHasher.contentHash(event));
        entityManager.persist(event);
        entityManager.flush();
        auditOutbox.stageApplyDecision(document, event);
        return event;
    }

    /** Apply, block, and refusal rows. One chain row and the outbox row from {@link #append}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AmendmentDocumentEvent appendApplyDecision(AmendmentEventAppend command) {
        return append(command);
    }

    private AmendmentDocumentEvent findBySource(UUID sourceEventId) {
        List<AmendmentDocumentEvent> found = entityManager.createQuery(
                        "select e from AmendmentDocumentEvent e where e.sourceEventId = :sourceEventId",
                        AmendmentDocumentEvent.class)
                .setParameter("sourceEventId", sourceEventId)
                .setMaxResults(1)
                .getResultList();
        return found.isEmpty() ? null : found.get(0);
    }

    private long nextSeq(UUID documentId) {
        Long max = entityManager.createQuery(
                        "select max(e.seqNo) from AmendmentDocumentEvent e where e.documentId = :documentId",
                        Long.class)
                .setParameter("documentId", documentId)
                .getSingleResult();
        return max == null ? 1L : max + 1L;
    }

    private String contentHashAt(UUID documentId, long seqNo) {
        return entityManager.createQuery(
                        "select e.contentHash from AmendmentDocumentEvent e "
                                + "where e.documentId = :documentId and e.seqNo = :seqNo",
                        String.class)
                .setParameter("documentId", documentId)
                .setParameter("seqNo", seqNo)
                .getSingleResult();
    }

    private static void validate(AmendmentEventAppend command) {
        if (command.documentId() == null) {
            throw new IllegalArgumentException("documentId is required");
        }
        requireText(command.eventType(), "eventType", 80);
        requireText(command.actorId(), "actorId", 255);
        if (command.actorType() == null) {
            throw new IllegalArgumentException("actorType is required");
        }
        requireText(command.actorRole(), "actorRole", 255);
        if (command.occurredAt() == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        if (command.payloadDigest() == null || !HEX.matcher(command.payloadDigest()).matches()) {
            throw new IllegalArgumentException("payloadDigest must be 64 lowercase hex characters");
        }
        requireText(command.payloadRef(), "payloadRef", 1024);
        requireText(command.correlationId(), "correlationId", 255);
        requireText(command.createdBy(), "createdBy", 255);
        if (command.reasonCode() != null && command.reasonCode().length() > 128) {
            throw new IllegalArgumentException("reasonCode exceeds 128 characters");
        }
    }

    private static void requireText(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (value.length() > max) {
            throw new IllegalArgumentException(name + " exceeds " + max + " characters");
        }
    }

    /** PostgreSQL timestamptz stores microseconds. Hash the value that will be read back. */
    private static Instant micros(Instant instant) {
        return Instant.ofEpochSecond(instant.getEpochSecond(), (instant.getNano() / 1000) * 1000L);
    }
}
