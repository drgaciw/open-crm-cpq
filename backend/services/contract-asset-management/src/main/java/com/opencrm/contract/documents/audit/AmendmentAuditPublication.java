package com.opencrm.contract.documents.audit;

import com.opencrm.contract.documents.events.AmendmentJson;

import java.util.UUID;

/**
 * Minimal audit payload for one amendment document event (DRG-577 ruling B / D-R7).
 * Fields are the document id, the amendment sequence once assigned, and the content digest,
 * plus the correlation id and the stable event id the outbox relay repeats.
 * No order id, saga id, command id, or fulfilment payload.
 */
public record AmendmentAuditPublication(
        UUID amendmentId,
        UUID documentId,
        Integer amendmentSequence,
        String amendmentNumber,
        String digest,
        String correlationId,
        UUID eventId
) {

    public String toJson() {
        return new AmendmentJson()
                .text("amendmentId", amendmentId.toString())
                .text("documentId", documentId.toString())
                .nullableInt("amendmentSequence", amendmentSequence)
                .text("amendmentNumber", amendmentNumber)
                .text("digest", digest)
                .text("correlationId", correlationId)
                .text("eventId", eventId.toString())
                .finish();
    }
}
