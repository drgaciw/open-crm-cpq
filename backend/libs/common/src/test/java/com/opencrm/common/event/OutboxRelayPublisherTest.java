package com.opencrm.common.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayPublisherTest {
    @Mock OutboxEventRepository repository;
    @Mock OutboxDeliveryStore store;
    @Mock KafkaTemplate<String,String> kafka;
    @InjectMocks OutboxRelayPublisher relay;
    final OutboxDeliveryStore.Delivery event = new OutboxDeliveryStore.Delivery(1,"lease","topic","aggregate","{}",1);
    void claimed() { when(store.claim(100,5,Duration.ofSeconds(60))).thenReturn(List.of(event)); }

    @Test void doesNotPublishBeforeBrokerAcknowledgment() {
        claimed();
        var acknowledgment = new CompletableFuture<SendResult<String,String>>();
        when(kafka.send("topic","aggregate","{}")).thenReturn(acknowledgment);
        relay.publishPendingEvents();
        verify(store,never()).acknowledged(any());
        verify(store,never()).failed(any(),anyInt(),anyString());
        acknowledgment.complete(mock(SendResult.class));
        verify(store).acknowledged(event);
        verify(repository,never()).saveAll(any());
    }
    @Test void asynchronousFailureRemainsUnpublished() {
        claimed();
        when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("secret payload")));
        relay.publishPendingEvents();
        verify(store,never()).acknowledged(any());
        verify(store).failed(event,5,"IllegalStateException");
    }
    @Test void synchronousFailureRemainsUnpublished() {
        claimed();
        when(kafka.send(anyString(),anyString(),anyString())).thenThrow(new IllegalArgumentException("secret payload"));
        relay.publishPendingEvents();
        verify(store,never()).acknowledged(any());
        verify(store).failed(event,5,"IllegalArgumentException");
    }
    @Test void ackPersistenceFailureLeavesClaimForCrashRecovery() {
        claimed();
        when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        when(store.acknowledged(event)).thenThrow(new IllegalStateException("Database unavailable"));
        relay.publishPendingEvents();
        verify(store).acknowledged(event);
        verify(store,never()).failed(any(),anyInt(),anyString());
    }
    @Test void emptyBatchDoesNotContactBroker() {
        when(store.claim(anyInt(),anyInt(),any())).thenReturn(List.of());
        relay.publishPendingEvents();
        verifyNoInteractions(kafka);
    }
    @Test void cleanupOnlyDeletesPublishedRows() {
        var event = new OutboxEvent();
        when(repository.findPublishedEventsBefore(any(Instant.class))).thenReturn(List.of(event));
        relay.cleanupOldEvents();
        verify(repository).deleteAll(List.of(event));
        verifyNoInteractions(store);
    }
}
