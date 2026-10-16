package com.opencrm.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.EventContractRegistry.EventContract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Service for publishing domain events via Transactional Outbox pattern.
 * Events are persisted in the same transaction as domain changes,
 * ensuring consistency and reliability.
 *
 * <p>ADR-018 D5: the topic comes from an {@link EventContractRegistry} entry, never from a string. Only a PUBLISHED
 * contract is accepted, and only with a payload of its registered class, so what is written here is what the registry
 * says is on the wire.
 *
 * <p>ADR-018 D4: each row also records the envelope the relay sends as headers: topic, contract version, correlation
 * and causation (from the active {@link EventCorrelation} scope; an event outside any scope starts its own chain,
 * correlated by its own id) and the actor ({@link EventActorResolver}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<EventActorResolver> actorResolver;

    /**
     * Publish an event to the outbox table.
     * Must be called within an active transaction.
     *
     * @param contract the registry entry for the event; must be PUBLISHED
     * @param eventPayload an instance of {@code contract.payloadClass()}
     * @throws IllegalStateException if the contract is RESERVED
     * @throws IllegalArgumentException if the payload is not of the contract's payload class
     */
    @Transactional
    public void publish(String aggregateType, String aggregateId, EventContract contract, Object eventPayload) {
        publish(aggregateType, aggregateId, contract, eventPayload, UUID.randomUUID());
    }

    /** Publish with the durable domain event id, so outbox replay and the audit chain share one identity. */
    @Transactional
    public void publish(String aggregateType, String aggregateId, EventContract contract, Object eventPayload,
                        UUID domainEventId) {
        Objects.requireNonNull(domainEventId, "domainEventId");
        Objects.requireNonNull(contract, "contract");
        if (!contract.isPublished()) {
            throw new IllegalStateException("Event contract " + contract.eventType() + " v" + contract.eventVersion()
                    + " on " + contract.topic() + " is RESERVED; register it as PUBLISHED with its payload class "
                    + "before publishing it (ADR-018 D5)");
        }
        if (!contract.payloadClass().isInstance(eventPayload)) {
            throw new IllegalArgumentException("Topic " + contract.topic() + " carries "
                    + contract.payloadClass().getName() + ", not "
                    + (eventPayload == null ? "null" : eventPayload.getClass().getName()));
        }
        try {
            String payload = objectMapper.writeValueAsString(eventPayload);
            String eventId = domainEventId.toString();
            EventCorrelation.Context correlation = EventCorrelation.current().orElse(null);
            String correlationId = correlation != null && correlation.correlationId() != null
                    ? correlation.correlationId()
                    : eventId;

            OutboxEvent outboxEvent = Objects.requireNonNull(OutboxEvent.builder()
                    .uuid(eventId)
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    // D4 expand step: event_type keeps holding the topic so a relay from before D4 still routes
                    // correctly; the D4 contract step switches it to contract.eventType().
                    .eventType(contract.topic())
                    .topic(contract.topic())
                    .eventVersion(contract.eventVersion())
                    .correlationId(correlationId)
                    .causationId(correlation != null ? correlation.causationId() : null)
                    .actor(currentActor())
                    .payload(payload)
                    .published(false)
                    .retryCount(0)
                    .build());

            outboxEventRepository.save(outboxEvent);

            log.debug("Published event to outbox: type={} v{}, topic={}, aggregateId={}, correlationId={}",
                    contract.eventType(), contract.eventVersion(), contract.topic(), aggregateId, correlationId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event payload", e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    private String currentActor() {
        EventActorResolver resolver = actorResolver.getIfAvailable();
        return resolver == null ? null : resolver.currentActor();
    }
}
