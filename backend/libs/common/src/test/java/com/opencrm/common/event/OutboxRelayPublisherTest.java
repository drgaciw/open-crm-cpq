package com.opencrm.common.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class OutboxRelayPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxRelayPublisher outboxRelayPublisher;

    private OutboxEvent createOutboxEvent(Long id, String eventType, String aggregateId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setEventType(eventType);
        event.setAggregateId(aggregateId);
        event.setPayload(payload);
        return event;
    }

    @Test
    void publishPendingEvents_NoEvents() {
        when(outboxEventRepository.findUnpublishedEventsWithRetryLimit(anyInt())).thenReturn(Collections.emptyList());

        outboxRelayPublisher.publishPendingEvents();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(outboxEventRepository, never()).saveAll(anyList());
    }

    @Test
    void publishPendingEvents_SuccessfulPublish() {
        OutboxEvent event1 = createOutboxEvent(1L, "TestType", "123", "{\"data\":\"test\"}");
        OutboxEvent event2 = createOutboxEvent(2L, "TestType2", "456", "{\"data\":\"test2\"}");

        when(outboxEventRepository.findUnpublishedEventsWithRetryLimit(anyInt())).thenReturn(Arrays.asList(event1, event2));

        SendResult<String, String> sendResult = mock();
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        outboxRelayPublisher.publishPendingEvents();

        verify(kafkaTemplate).send("TestType", "123", "{\"data\":\"test\"}");
        verify(kafkaTemplate).send("TestType2", "456", "{\"data\":\"test2\"}");

        verify(outboxEventRepository).saveAll(eq(Arrays.asList(event1, event2)));

        assertTrue(event1.getPublished());
        assertNotNull(event1.getPublishedAt());
        assertTrue(event2.getPublished());
        assertNotNull(event2.getPublishedAt());
    }

    @Test
    void publishPendingEvents_AsynchronousFailure() {
        OutboxEvent event = createOutboxEvent(1L, "TestType", "123", "{\"data\":\"test\"}");

        when(outboxEventRepository.findUnpublishedEventsWithRetryLimit(anyInt())).thenReturn(Collections.singletonList(event));

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka error"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        outboxRelayPublisher.publishPendingEvents();

        verify(kafkaTemplate).send("TestType", "123", "{\"data\":\"test\"}");

        // As per current implementation, an async failure is just logged. The event is still marked as published and saved.
        verify(outboxEventRepository).saveAll(eq(Collections.singletonList(event)));
        assertTrue(event.getPublished());
        assertNotNull(event.getPublishedAt());
    }

    @Test
    void publishPendingEvents_SynchronousFailure() {
        OutboxEvent event = createOutboxEvent(1L, "TestType", "123", "{\"data\":\"test\"}");
        event.setRetryCount(0);

        when(outboxEventRepository.findUnpublishedEventsWithRetryLimit(anyInt())).thenReturn(Collections.singletonList(event));

        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("Synchronous Kafka error"));

        outboxRelayPublisher.publishPendingEvents();

        verify(kafkaTemplate).send("TestType", "123", "{\"data\":\"test\"}");

        verify(outboxEventRepository).saveAll(eq(Collections.singletonList(event)));
        assertFalse(event.getPublished());
        assertEquals(1, event.getRetryCount());
        assertEquals("Synchronous Kafka error", event.getErrorMessage());
    }

    @Test
    void cleanupOldEvents_EventsFound() {
        OutboxEvent event = new OutboxEvent();
        event.setId(1L);

        when(outboxEventRepository.findPublishedEventsBefore(any(Instant.class))).thenReturn(Collections.singletonList(event));

        outboxRelayPublisher.cleanupOldEvents();

        verify(outboxEventRepository).deleteAll(Collections.singletonList(event));
    }

    @Test
    void cleanupOldEvents_NoEventsFound() {
        when(outboxEventRepository.findPublishedEventsBefore(any(Instant.class))).thenReturn(Collections.emptyList());

        outboxRelayPublisher.cleanupOldEvents();

        verify(outboxEventRepository, never()).deleteAll(anyList());
    }
}
