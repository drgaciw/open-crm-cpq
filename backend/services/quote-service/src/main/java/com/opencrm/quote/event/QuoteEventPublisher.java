package com.opencrm.quote.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes {@link QuoteAcceptedCrmMessage} on the Redis channel {@code quote:accepted} for crm-service.
 *
 * <p>ADR-018 D8, transition step 1. The message used to be published <em>inside</em> the accept transaction, before
 * the outbox write, and a Redis error threw and rolled the acceptance back. Quote acceptance depended on Redis, and
 * CRM could see an acceptance that later rolled back. Now it is published only after the transaction commits, and a
 * Redis failure is logged, never thrown. The durable channel is the outbox and Kafka
 * ({@code cpq.quote.status.accepted}), which crm-service also consumes during the dual run. This publisher and the
 * Redis listener are removed at the end of that run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuoteEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public static final String QUOTE_ACCEPTED_CHANNEL = "quote:accepted";

    /** Publishes after the current transaction commits (never on rollback), or at once when there is none. */
    public void publishQuoteAccepted(QuoteAcceptedCrmMessage event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishNow(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishNow(event);
            }
        });
    }

    /** Best effort: the outbox and Kafka are the reliable channel, so a Redis failure is logged, never thrown. */
    void publishNow(QuoteAcceptedCrmMessage event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(QUOTE_ACCEPTED_CHANNEL, message);
            log.info("Published QuoteAcceptedCrmMessage for quote: {}", event.getQuoteNumber());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize QuoteAcceptedCrmMessage for quote {}; CRM relies on Kafka for it",
                    event.getQuoteNumber(), e);
        } catch (RuntimeException e) {
            log.warn("Redis quote:accepted publish failed for quote {}; CRM relies on Kafka for it: {}",
                    event.getQuoteNumber(), e.getMessage());
        }
    }
}
