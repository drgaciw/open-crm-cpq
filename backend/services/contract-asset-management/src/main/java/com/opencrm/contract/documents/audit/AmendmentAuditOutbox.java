package com.opencrm.contract.documents.audit;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.EventCorrelation;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.AmendmentAuditEvent;
import com.opencrm.contract.domain.documents.AmendmentDocument;
import com.opencrm.contract.domain.documents.AmendmentDocumentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Stages the typed S2 audit contract in the domain transaction. This does not enable the relay. */
@Service
public class AmendmentAuditOutbox {
    public static final String TOPIC = KafkaTopics.AMENDMENT_DOCUMENT_AUDIT;
    public static final String AGGREGATE_TYPE = "AmendmentDocument";
    /** Longer business correlations remain intact in the payload, with event-id correlation in the envelope. */
    static final int ENVELOPE_CORRELATION_LIMIT = 100;
    private final OutboxEventPublisher publisher;

    public AmendmentAuditOutbox(OutboxEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void stage(AmendmentDocument document, AmendmentDocumentEvent event) {
        AmendmentAuditEvent publication = new AmendmentAuditEvent(
                document.getId(), document.getId(), document.getAmendmentSeq(), document.getAmendmentNumber(),
                event.getContentHash(), event.getCorrelationId(), event.getEventId());
        String causation = event.getSourceEventId() == null ? null : event.getSourceEventId().toString();
        try (EventCorrelation.Scope ignored = EventCorrelation.begin(
                envelopeCorrelation(event.getCorrelationId()), causation)) {
            publisher.publish(AGGREGATE_TYPE, document.getId().toString(),
                    EventContractRegistry.AMENDMENT_AUDIT_RECORDED_V1, publication, event.getEventId());
        }
    }

    static String envelopeCorrelation(String correlationId) {
        return correlationId == null || correlationId.length() > ENVELOPE_CORRELATION_LIMIT ? null : correlationId;
    }
}
