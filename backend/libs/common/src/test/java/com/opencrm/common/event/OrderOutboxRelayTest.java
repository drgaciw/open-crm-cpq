package com.opencrm.common.event;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderOutboxRelayTest {
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxDeliveryStore store = mock(OutboxDeliveryStore.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxDeliveryStore.Delivery delivery =
            new OutboxDeliveryStore.Delivery(1L, "lease", "topic", "order", "payload", 1);
    private final OrderOutboxRelay relay = new OrderOutboxRelay(repository, kafka, store);

    @Test
    void leavesDeliveryUnpublishedUntilTheBrokerAcknowledges() {
        var acknowledgment = new CompletableFuture<SendResult<String, String>>();
        when(store.claim(100, 5, Duration.ofSeconds(60))).thenReturn(List.of(delivery));
        when(kafka.send("topic", "order", "payload")).thenReturn(acknowledgment);
        when(store.acknowledged(delivery)).thenReturn(true);

        relay.publishPendingEvents();
        verify(store, never()).acknowledged(any());
        acknowledgment.complete(null);
        verify(store).acknowledged(delivery);
        verify(store, never()).failed(any(), anyInt(), anyString());
    }

    @Test
    void failedBrokerAcknowledgmentRemainsAFailedAttempt() {
        when(store.claim(100, 5, Duration.ofSeconds(60))).thenReturn(List.of(delivery));
        when(kafka.send("topic", "order", "payload"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("unavailable")));
        when(store.failed(delivery, 5, "IllegalStateException")).thenReturn(true);

        relay.publishPendingEvents();

        verify(store).failed(delivery, 5, "IllegalStateException");
        verify(store, never()).acknowledged(any());
    }

    @Test
    void cleanupUsesTheRepositorysPublishedOnlyBoundary() {
        relay.cleanupOldEvents();
        verify(repository).deletePublishedBefore(any());
        verify(repository, never()).deleteAll(anyIterable());
    }
}
