package com.opencrm.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.dto.ProductRetiredEvent;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        String aggregateType = "Quote";
        String aggregateId = "Q-123";
        QuoteAcceptedEvent eventPayload = new QuoteAcceptedEvent();
        String jsonPayload = "{\"key\":\"value\"}";

        when(objectMapper.writeValueAsString(eventPayload)).thenReturn(jsonPayload);

        // Act
        outboxEventPublisher.publish(aggregateType, aggregateId, EventContractRegistry.QUOTE_ACCEPTED_V1, eventPayload);

        // Assert
        verify(outboxEventRepository, times(1)).save(outboxEventCaptor.capture());
        OutboxEvent capturedEvent = outboxEventCaptor.getValue();

        assertEquals(aggregateType, capturedEvent.getAggregateType());
        assertEquals(aggregateId, capturedEvent.getAggregateId());
        assertEquals(KafkaTopics.QUOTE_STATUS_ACCEPTED, capturedEvent.getEventType(),
                "outbox_events.event_type holds the topic the relay sends to, until ADR-018 D4");
        assertEquals(jsonPayload, capturedEvent.getPayload());
        assertFalse(capturedEvent.getPublished());
        assertEquals(0, capturedEvent.getRetryCount());
    }

    @Test
    void publish_ThrowsRuntimeException_WhenSerializationFails() throws JsonProcessingException {
        // Arrange
        QuoteAcceptedEvent eventPayload = new QuoteAcceptedEvent();

        JsonProcessingException jsonProcessingException = mock(JsonProcessingException.class);
        when(objectMapper.writeValueAsString(eventPayload)).thenThrow(jsonProcessingException);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                outboxEventPublisher.publish("Quote", "Q-123", EventContractRegistry.QUOTE_ACCEPTED_V1, eventPayload)
        );

        assertEquals("Failed to publish event", exception.getMessage());
        assertEquals(jsonProcessingException, exception.getCause());
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void publish_RefusesAReservedContract() {
        assertThatThrownBy(() -> outboxEventPublisher.publish(
                "PriceCalculation", "Q-123", EventContractRegistry.PRICE_CALCULATED_V1, new Object()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESERVED")
                .hasMessageContaining(KafkaTopics.PRICE_CALCULATED);

        verifyNoInteractions(objectMapper, outboxEventRepository);
    }

    @Test
    void publish_RefusesAPayloadOfTheWrongClass() {
        assertThatThrownBy(() -> outboxEventPublisher.publish(
                "Quote", "Q-123", EventContractRegistry.QUOTE_ACCEPTED_V1, new ProductRetiredEvent()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(QuoteAcceptedEvent.class.getName())
                .hasMessageContaining(ProductRetiredEvent.class.getName());

        verifyNoInteractions(objectMapper, outboxEventRepository);
    }

    @Test
    void publish_RefusesANullPayload() {
        assertThatThrownBy(() -> outboxEventPublisher.publish(
                "Quote", "Q-123", EventContractRegistry.QUOTE_ACCEPTED_V1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not null");

        verifyNoInteractions(objectMapper, outboxEventRepository);
    }
}
