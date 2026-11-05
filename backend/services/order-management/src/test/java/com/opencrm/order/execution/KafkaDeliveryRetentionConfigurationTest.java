package com.opencrm.order.execution;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KafkaDeliveryRetentionConfigurationTest {
    @Test void saturationIsNeverRecoveredOrCommittedAfterDefaultRetryLimit() {
        var handler = new KafkaDeliveryRetentionConfiguration().kafkaDeliveryRetentionErrorHandler();
        var consumer = mock(Consumer.class);
        var container = mock(MessageListenerContainer.class);
        var record = new ConsumerRecord<>("accepted", 0, 3L, "quote", "payload");
        for (int attempt = 0; attempt < 25; attempt++) {
            assertFalse(handler.handleOne(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE), record, consumer, container), "must retain attempt " + attempt);
        }
        assertFalse(handler.isAckAfterHandle());
        verifyNoInteractions(consumer);
    }

    @Test void malformedTrustedRecordIsRetainedInsteadOfFatalRecovery() {
        var handler = new KafkaDeliveryRetentionConfiguration().kafkaDeliveryRetentionErrorHandler();
        assertFalse(handler.handleOne(new ClassCastException("invalid envelope"), new ConsumerRecord<>("accepted", 0, 4L, "quote", "invalid"), mock(Consumer.class), mock(MessageListenerContainer.class)));
    }
}
