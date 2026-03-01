package com.opencrm.product.scheduler;

import com.opencrm.product.repository.OutboxEventRepository;
import com.opencrm.shared.outbox.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishEvents() {
        List<OutboxEvent> events = outboxEventRepository.findByProcessedAtIsNull();
        for (OutboxEvent event : events) {
            try {
                // Synchronous send to ensure ordering and success before marking processed
                kafkaTemplate.send(event.getType(), event.getAggregateId(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);
                        
                event.setProcessedAt(Instant.now());
                outboxEventRepository.save(event);
                log.info("Published event: {} for aggregate {}", event.getType(), event.getAggregateId());
            } catch (Exception e) {
                log.error("Failed to publish event: {}", event.getId(), e);
                // Do not mark processed, will retry next run
            }
        }
    }
}
