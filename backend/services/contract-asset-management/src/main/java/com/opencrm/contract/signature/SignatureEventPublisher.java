package com.opencrm.contract.signature;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.SignatureCompletedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single outbox emitter for {@code SignatureCompleted} (ADR-018 D9: exactly one publish call site, named directly
 * so {@code EventContractFitnessTest} can see it). Called from {@code SignatureCoordinationService} inside the
 * same transaction that claims the idempotency ledger row and transitions the envelope to COMPLETED, so a
 * redelivered webhook that is rejected by the ledger's unique constraint never reaches this method at all.
 */
@Service
public class SignatureEventPublisher {

    static final String AGGREGATE_TYPE = "SignatureEnvelope";

    private final OutboxEventPublisher publisher;

    public SignatureEventPublisher(OutboxEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publishCompleted(SignatureEnvelope envelope, SignatureCompletedEvent event) {
        UUID eventId = UUID.randomUUID();
        publisher.publish(AGGREGATE_TYPE, envelope.getUuid(), EventContractRegistry.SIGNATURE_COMPLETED_V1, event,
                eventId);
        return eventId;
    }
}
