package com.opencrm.contract.documents.audit;

import com.opencrm.common.event.OutboxEvent;
import com.opencrm.common.event.OutboxEventRepository;
import com.opencrm.common.event.OutboxStatus;
import com.opencrm.contract.domain.documents.ActorType;
import com.opencrm.contract.domain.documents.AmendmentDocument;
import com.opencrm.contract.domain.documents.AmendmentDocumentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stages one audit row in the existing transactional outbox. The row stays {@code PENDING}.
 * The relay is off unless {@code opencrm.outbox.relay.enabled} is set, and this story does not set it.
 * The schema-registry contract for the topic is DRG-584; this class does not register one.
 */
@Service
public class AmendmentAuditOutbox {

    public static final String TOPIC = "cam.amendment-document.audit";

    public static final String AGGREGATE_TYPE = "AmendmentDocument";

    /** {@code outbox_events.correlation_id} is varchar(100). Longer business ids stay in the payload. */
    static final int ENVELOPE_CORRELATION_LIMIT = 100;

    private final OutboxEventRepository outboxEvents;

    public AmendmentAuditOutbox(OutboxEventRepository outboxEvents) {
        this.outboxEvents = outboxEvents;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void stage(AmendmentDocument document, AmendmentDocumentEvent event) {
        AmendmentAuditPublication publication = new AmendmentAuditPublication(
                document.getId(),
                document.getId(),
                document.getAmendmentSeq(),
                document.getAmendmentNumber(),
                event.getContentHash(),
                event.getCorrelationId(),
                event.getEventId());
        String causation = event.getSourceEventId() == null ? null : event.getSourceEventId().toString();
        OutboxEvent row = OutboxEvent.builder()
                .uuid(event.getEventId().toString())
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(document.getId().toString())
                .eventType(TOPIC)
                .topic(TOPIC)
                .correlationId(envelopeCorrelation(event.getCorrelationId()))
                .causationId(causation)
                .actor(actor(event))
                .payload(publication.toJson())
                .published(false)
                .retryCount(0)
                .status(OutboxStatus.PENDING)
                .deleted(false)
                .build();
        outboxEvents.save(row);
    }

    static String envelopeCorrelation(String correlationId) {
        if (correlationId == null || correlationId.length() > ENVELOPE_CORRELATION_LIMIT) {
            return null;
        }
        return correlationId;
    }

    private static String actor(AmendmentDocumentEvent event) {
        ActorType actorType = event.getActorType();
        return switch (actorType) {
            case USER -> "user:" + event.getActorId();
            case SYSTEM, SERVICE -> "service:" + event.getActorId();
        };
    }
}
