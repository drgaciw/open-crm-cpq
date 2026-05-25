package com.opencrm.common.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Outbox Relay: polls the outbox_events table for unpublished events
 * and forwards them to Kafka. Runs on a fixed schedule.
 *
 * Ensures at-least-once delivery semantics for the Transactional Outbox pattern.
 */
@Component
@ConditionalOnBean(KafkaTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final int MAX_RETRIES = 5;
    private static final int BATCH_SIZE = 100;

    /**
     * Poll for unpublished outbox events every 5 seconds and publish to Kafka.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findUnpublishedEventsWithRetryLimit(MAX_RETRIES);

        if (events.isEmpty()) {
            return;
        }

        int processed = 0;
        List<OutboxEvent> updatedEvents = new ArrayList<>();

        for (OutboxEvent event : events) {
            if (processed >= BATCH_SIZE) {
                break;
            }

            try {
                kafkaTemplate.send(event.getEventType(), event.getAggregateId(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Failed to publish outbox event {} to Kafka topic {}",
                                        event.getId(), event.getEventType(), ex);
                            }
                        });

                event.markAsPublished();
                updatedEvents.add(event);
                processed++;

                log.debug("Published outbox event {} to topic {}", event.getId(), event.getEventType());

            } catch (Exception e) {
                log.error("Error publishing outbox event {}", event.getId(), e);
                event.incrementRetryCount(e.getMessage());
                updatedEvents.add(event);
            }
        }

        if (!updatedEvents.isEmpty()) {
            outboxEventRepository.saveAll(updatedEvents);
        }

        if (processed > 0) {
            log.info("Outbox relay published {} events", processed);
        }
    }

    /**
     * Cleanup old published events (older than 7 days). Runs daily at 3 AM.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldEvents() {
        Instant cutoff = Instant.now().minusSeconds(7 * 24 * 3600);
        List<OutboxEvent> oldEvents = outboxEventRepository.findPublishedEventsBefore(cutoff);

        if (!oldEvents.isEmpty()) {
            outboxEventRepository.deleteAll(oldEvents);
            log.info("Cleaned up {} old outbox events", oldEvents.size());
        }
    }
}
