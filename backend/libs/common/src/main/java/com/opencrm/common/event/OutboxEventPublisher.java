package com.opencrm.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.EventContractRegistry.EventContract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Service for publishing domain events via Transactional Outbox pattern.
 * Events are persisted in the same transaction as domain changes,
 * ensuring consistency and reliability.
 *
 * <p>ADR-018 D5: the topic comes from an {@link EventContractRegistry} entry, never from a string. Only a PUBLISHED
 * contract is accepted, and only with a payload of its registered class, so what is written here is what the registry
 * says is on the wire.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

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

            // Until ADR-018 D4 adds a topic column, outbox_events.event_type holds the topic the relay sends to.
            OutboxEvent outboxEvent = Objects.requireNonNull(OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(contract.topic())
                    .payload(payload)
                    .published(false)
                    .retryCount(0)
                    .build());

            outboxEventRepository.save(outboxEvent);

            log.debug("Published event to outbox: type={} v{}, topic={}, aggregateId={}",
                    contract.eventType(), contract.eventVersion(), contract.topic(), aggregateId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event payload", e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}
