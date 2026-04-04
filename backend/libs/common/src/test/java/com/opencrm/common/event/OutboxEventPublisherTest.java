package com.opencrm.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxEventPublisher outboxEventPublisher;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

    @Test
    void publish_Success() throws JsonProcessingException {
        // Arrange
        String aggregateType = "Order";
        String aggregateId = "123";
        String eventType = "OrderCreated";
        Object eventPayload = new Object();
        String jsonPayload = "{\"key\":\"value\"}";

        when(objectMapper.writeValueAsString(eventPayload)).thenReturn(jsonPayload);

        // Act
        outboxEventPublisher.publish(aggregateType, aggregateId, eventType, eventPayload);

        // Assert
        verify(outboxEventRepository, times(1)).save(outboxEventCaptor.capture());
        OutboxEvent capturedEvent = outboxEventCaptor.getValue();

        assertEquals(aggregateType, capturedEvent.getAggregateType());
        assertEquals(aggregateId, capturedEvent.getAggregateId());
        assertEquals(eventType, capturedEvent.getEventType());
        assertEquals(jsonPayload, capturedEvent.getPayload());
        assertFalse(capturedEvent.getPublished());
        assertEquals(0, capturedEvent.getRetryCount());
    }

    @Test
    void publish_ThrowsRuntimeException_WhenSerializationFails() throws JsonProcessingException {
        // Arrange
        String aggregateType = "Order";
        String aggregateId = "123";
        String eventType = "OrderCreated";
        Object eventPayload = new Object();

        JsonProcessingException jsonProcessingException = mock(JsonProcessingException.class);
        when(objectMapper.writeValueAsString(eventPayload)).thenThrow(jsonProcessingException);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                outboxEventPublisher.publish(aggregateType, aggregateId, eventType, eventPayload)
        );

        assertEquals("Failed to publish event", exception.getMessage());
        assertEquals(jsonProcessingException, exception.getCause());
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }
}
