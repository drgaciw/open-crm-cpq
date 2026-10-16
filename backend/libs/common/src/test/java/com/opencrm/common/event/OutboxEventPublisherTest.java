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
import org.springframework.beans.factory.ObjectProvider;

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

    @Mock
    private ObjectProvider<EventActorResolver> actorResolver;

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
                "event_type keeps holding the topic until the ADR-018 D4 contract step");
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

    @Test
    void publish_RecordsTheD4Envelope() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(actorResolver.getIfAvailable()).thenReturn(() -> "user:auth0|42");

        outboxEventPublisher.publish("Quote", "Q-1", EventContractRegistry.QUOTE_ACCEPTED_V1, new QuoteAcceptedEvent());

        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        OutboxEvent row = outboxEventCaptor.getValue();
        assertEquals(KafkaTopics.QUOTE_STATUS_ACCEPTED, row.getTopic());
        assertEquals(1, row.getEventVersion());
        assertNotNull(row.getUuid(), "the event id is assigned before save so it can seed the correlation");
        assertEquals(row.getUuid(), row.getCorrelationId(), "outside any scope an event starts its own chain");
        assertNull(row.getCausationId());
        assertEquals("user:auth0|42", row.getActor());
    }

    @Test
    void publish_TakesCorrelationAndCausationFromTheActiveScope() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        try (EventCorrelation.Scope ignored = EventCorrelation.begin("quote-uuid-7", "upstream-event-9")) {
            outboxEventPublisher.publish("Quote", "Q-1", EventContractRegistry.QUOTE_ACCEPTED_V1, new QuoteAcceptedEvent());
        }

        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        OutboxEvent row = outboxEventCaptor.getValue();
        assertEquals("quote-uuid-7", row.getCorrelationId());
        assertEquals("upstream-event-9", row.getCausationId());
        assertNull(row.getActor(), "no EventActorResolver bean: no actor");
        assertTrue(EventCorrelation.current().isEmpty(), "closing the scope restores the empty context");
    }
    @Test
    void publish_PreservesDurableDomainIdentityAndEnvelope() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        var eventId = java.util.UUID.randomUUID();
        try (EventCorrelation.Scope ignored = EventCorrelation.begin(null, "source-event")) {
            outboxEventPublisher.publish("Quote", "Q-1", EventContractRegistry.QUOTE_ACCEPTED_V1,
                    new QuoteAcceptedEvent(), eventId);
        }
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        OutboxEvent row = outboxEventCaptor.getValue();
        assertEquals(eventId.toString(), row.getUuid());
        assertEquals(eventId.toString(), row.getCorrelationId());
        assertEquals("source-event", row.getCausationId());
        assertEquals(1, row.getEventVersion());
        assertTrue(EventCorrelation.current().isEmpty());
    }

}