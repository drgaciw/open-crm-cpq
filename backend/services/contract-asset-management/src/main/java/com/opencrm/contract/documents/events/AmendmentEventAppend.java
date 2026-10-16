package com.opencrm.contract.documents.events;

import com.opencrm.contract.domain.documents.ActorType;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One append to a document chain. The caller owns the state change in the same transaction.
 * This command does not carry an order, saga, or fulfilment identity.
 */
public record AmendmentEventAppend(
        UUID documentId,
        String eventType,
        AmendmentDocumentStatus fromStatus,
        AmendmentDocumentStatus toStatus,
        Integer revisionNo,
        Integer unitNo,
        String actorId,
        ActorType actorType,
        String actorRole,
        Instant occurredAt,
        String reasonCode,
        String payloadDigest,
        String payloadRef,
        UUID sourceEventId,
        String correlationId,
        String createdBy
) {
}
