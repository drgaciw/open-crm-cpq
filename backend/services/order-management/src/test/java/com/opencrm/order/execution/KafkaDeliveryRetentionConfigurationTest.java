package com.opencrm.order.execution;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KafkaDeliveryRetentionConfigurationTest {
    @Test void saturationIsDeadLetteredAndCommittedAfterBoundedRetry() {
        KafkaOperations<Object,Object> template = mock(KafkaOperations.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        var handler = new KafkaDeliveryRetentionConfiguration().kafkaDeliveryRetentionErrorHandler(template);
        var consumer = mock(Consumer.class);
        var container = mock(MessageListenerContainer.class);
        var record = new ConsumerRecord<>("accepted", 0, 3L, "quote", "payload");
        for (int delivery = 1; delivery < 5; delivery++) {
            assertFalse(handler.handleOne(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE), record, consumer, container), "retry before limit " + delivery);
        }
        verify(template, never()).send(any(ProducerRecord.class));
        assertTrue(handler.handleOne(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE), record, consumer, container), "fifth delivery is dead-lettered");
        var sent = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template, times(1)).send(sent.capture());
        assertEquals("accepted.DLT", sent.getValue().topic());
        assertEquals("payload", sent.getValue().value());
        assertFalse(handler.isAckAfterHandle());
    }

    @Test void malformedRecordIsDeadLetteredAfterBoundedRetry() {
        KafkaOperations<Object,Object> template = mock(KafkaOperations.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        var handler = new KafkaDeliveryRetentionConfiguration().kafkaDeliveryRetentionErrorHandler(template);
        var consumer = mock(Consumer.class);
        var container = mock(MessageListenerContainer.class);
        var record = new ConsumerRecord<>("accepted", 0, 4L, "quote", "invalid");
        for (int delivery = 1; delivery < 5; delivery++) {
            assertFalse(handler.handleOne(new ClassCastException("invalid envelope"), record, consumer, container), "retry before limit " + delivery);
        }
        assertTrue(handler.handleOne(new ClassCastException("invalid envelope"), record, consumer, container));
        verify(template, times(1)).send(any(ProducerRecord.class));
    }
}
