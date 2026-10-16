package com.opencrm.contract.documents.audit;

import com.opencrm.contract.documents.events.AmendmentEventHasher;
import com.opencrm.contract.domain.documents.AmendmentDocumentEvent;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Recomputes each document chain and reports the first gap or broken link.
 * Not scheduled. A later operations switch can call {@link #verifyAll()}.
 */
@Service
public class AmendmentChainVerifier {

    private final EntityManager entityManager;

    public AmendmentChainVerifier(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Optional<AmendmentChainBreak> verify(UUID documentId) {
        return firstBreak(documentId, load(documentId));
    }

    @Transactional(readOnly = true)
    public AmendmentChainReport verifyAll() {
        List<UUID> documentIds = entityManager.createQuery(
                        "select distinct e.documentId from AmendmentDocumentEvent e", UUID.class)
                .getResultList();
        List<AmendmentChainBreak> breaks = new ArrayList<>();
        for (UUID documentId : documentIds) {
            firstBreak(documentId, load(documentId)).ifPresent(breaks::add);
        }
        return new AmendmentChainReport(documentIds.size(), breaks);
    }

    /**
     * @param events rows for one document; order does not matter
     * @return empty when the chain is empty or intact
     */
    public static Optional<AmendmentChainBreak> firstBreak(UUID documentId, List<AmendmentDocumentEvent> events) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(events, "events");
        List<AmendmentDocumentEvent> ordered = new ArrayList<>(events);
        ordered.sort(Comparator.comparing(AmendmentDocumentEvent::getSeqNo));
        long expected = 1;
        String previousContent = null;
        for (AmendmentDocumentEvent event : ordered) {
            if (!documentId.equals(event.getDocumentId())) {
                throw new IllegalArgumentException("event " + event.getEventId() + " is not for " + documentId);
            }
            long seqNo = event.getSeqNo();
            if (seqNo != expected) {
                return Optional.of(new AmendmentChainBreak(documentId, expected, AmendmentChainBreak.Kind.GAP));
            }
            String expectedPrev = expected == 1 ? AmendmentEventHasher.GENESIS_PREV_HASH : previousContent;
            if (!expectedPrev.equals(event.getPrevHash())) {
                return Optional.of(new AmendmentChainBreak(
                        documentId, seqNo, AmendmentChainBreak.Kind.BROKEN_PREV_HASH));
            }
            if (!AmendmentEventHasher.contentHash(event).equals(event.getContentHash())) {
                return Optional.of(new AmendmentChainBreak(
                        documentId, seqNo, AmendmentChainBreak.Kind.CONTENT_HASH_MISMATCH));
            }
            previousContent = event.getContentHash();
            expected++;
        }
        return Optional.empty();
    }

    private List<AmendmentDocumentEvent> load(UUID documentId) {
        return entityManager.createQuery(
                        "select e from AmendmentDocumentEvent e where e.documentId = :documentId order by e.seqNo",
                        AmendmentDocumentEvent.class)
                .setParameter("documentId", documentId)
                .getResultList();
    }
}
