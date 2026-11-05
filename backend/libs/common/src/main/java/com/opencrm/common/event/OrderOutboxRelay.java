package com.opencrm.common.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * At-least-once relay. Claims commit before sending; only broker acknowledgment marks published.
 * A crash after broker acknowledgment can redeliver, so consumers must deduplicate event identity.
 */
@RequiredArgsConstructor
@Slf4j
public class OrderOutboxRelay {
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxDeliveryStore deliveryStore;
    private static final int MAX_RETRIES = 5;
    private static final int BATCH_SIZE = 100;

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        for (var delivery : deliveryStore.claim(BATCH_SIZE, MAX_RETRIES, Duration.ofSeconds(60))) {
            try {
                kafkaTemplate.send(delivery.topic(), delivery.aggregateId(), delivery.payload())
                        .orTimeout(30, TimeUnit.SECONDS)
                        .whenComplete((result, error) -> complete(delivery, error));
            } catch (Exception error) {
                complete(delivery, error);
            }
        }
    }

    private void complete(OutboxDeliveryStore.Delivery delivery, Throwable error) {
        try {
            boolean updated = error == null
                    ? deliveryStore.acknowledged(delivery)
                    : deliveryStore.failed(delivery, MAX_RETRIES, error.getClass().getSimpleName());
            if (!updated) log.warn("Outbox event {} completion fenced; lease expired or superseded", delivery.id());
            else if (error != null) log.warn("Outbox event {} attempt {} failed ({})", delivery.id(), delivery.attempt(), error.getClass().getSimpleName());
        } catch (Exception persistenceFailure) {
            // Do not turn an ambiguous broker outcome into success. The committed lease is recoverable.
            log.error("Outbox event {} completion persistence failed ({})", delivery.id(), persistenceFailure.getClass().getSimpleName());
        }
    }

    /** Only acknowledged rows are eligible; pending/exhausted records are retained. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldEvents() {
        Instant cutoff = Instant.now().minusSeconds(7 * 24 * 3600);
        int deleted = outboxEventRepository.deletePublishedBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} acknowledged outbox events", deleted);
        }
    }
}
