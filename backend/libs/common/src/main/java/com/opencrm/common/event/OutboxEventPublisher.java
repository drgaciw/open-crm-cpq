package com.opencrm.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for publishing domain events via Transactional Outbox pattern.
 * Events are persisted in the same transaction as domain changes,
 * ensuring consistency and reliability.
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
     */
    @Transactional
    public void publish(String aggregateType, String aggregateId, String eventType, Object eventPayload) {
        try {
            String payload = objectMapper.writeValueAsString(eventPayload);
            
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateType(aggregateType);
            outboxEvent.setAggregateId(aggregateId);
            outboxEvent.setEventType(eventType);
            outboxEvent.setPayload(payload);
            
            outboxEventRepository.save(outboxEvent);
            
            log.debug("Published event to outbox: type={}, aggregateId={}", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event payload", e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}
