package com.opencrm.crm.event;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import java.util.Map;
import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("crm QuoteAcceptedKafkaListener (ADR-018 D8)")
class QuoteAcceptedKafkaListenerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Mock
    private QuoteAcceptedDealProcessor processor;

    private QuoteAcceptedKafkaListener listener;
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        listener = new QuoteAcceptedKafkaListener(objectMapper, processor,
                new QuoteAcceptedChannelMetrics(new StaticListableBeanFactory(Map.of("registry", registry))
                        .getBeanProvider(MeterRegistry.class)));
    }

    static QuoteAcceptedEvent event(String dealExternalId) {
        Instant acceptedAt = Instant.parse("2026-09-22T12:00:00Z");
        QuoteAcceptedEvent event = QuoteAcceptedEvent.v1(
                QuoteAcceptedEvent.QuoteSnapshot.builder()
                        .quoteId("quote-uuid-5").quoteNumber("Q-5").acceptedAt(acceptedAt).build(),
                QuoteAcceptedEvent.CustomerSnapshot.builder().customerId("cust-5").build(),
                QuoteAcceptedEvent.PricingSnapshot.builder()
                        .totalAmount(new BigDecimal("1200.00")).currencyCode("EUR")
                        .arr(new BigDecimal("1200.00")).tcv(new BigDecimal("3600.00")).acv(new BigDecimal("1200.00"))
                        .build(),
                null,
                QuoteAcceptedEvent.TermSnapshot.builder().termMonths(36).build(),
                null);
        if (dealExternalId != null) {
            event.setDeal(QuoteAcceptedEvent.DealSnapshot.builder()
                    .dealExternalId(dealExternalId).dealType("NEW").build());
        }
        return event;
    }

    @Test
    @DisplayName("maps the Kafka contract onto the deal update, keyed by the quote UUID like the Redis message")
    void mapsEventToDealUpdate() throws Exception {
        when(processor.applyWinFromQuote(any(), anyString())).thenReturn(QuoteAcceptedOutcome.DUPLICATE);
        listener.onQuoteAccepted(objectMapper.writeValueAsString(event("deal-5")));

        ArgumentCaptor<com.opencrm.common.event.dto.QuoteAcceptedCrmMessage> sent =
                ArgumentCaptor.forClass(com.opencrm.common.event.dto.QuoteAcceptedCrmMessage.class);
        verify(processor).applyWinFromQuote(sent.capture(), eq("deal-5"));
        var message = sent.getValue();
        assertThat(message.getQuoteUuid()).isEqualTo("quote-uuid-5");
        assertThat(message.getQuoteNumber()).isEqualTo("Q-5");
        assertThat(message.getTotalAmount()).isEqualByComparingTo("1200.00");
        assertThat(message.getCurrency()).isEqualTo("EUR");
        assertThat(message.getAcceptedAt()).isEqualTo(Instant.parse("2026-09-22T12:00:00Z"));
        assertThat(message.getTcv()).isEqualByComparingTo("3600.00");
        assertThat(message.getDurationInMonths()).isEqualTo(36);
        assertThat(QuoteAcceptedIdempotency.dedupeKey(message, "deal-5")).isEqualTo("quote-uuid-5");
        assertThat(count("duplicate")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a quote accepted without a CRM deal is skipped")
    void eventWithoutDealIsSkipped() throws Exception {
        listener.onQuoteAccepted(objectMapper.writeValueAsString(event(null)));

        verify(processor, never()).applyWinFromQuote(any(), anyString());
        assertThat(count("no-deal")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("D7: an unreadable payload or a missing quoteId is not retryable")
    void unreadableEventIsNotRetryable() {
        assertThatThrownBy(() -> listener.onQuoteAccepted("{not json")).isInstanceOf(NonRetryableEventException.class);
        assertThatThrownBy(() -> listener.onQuoteAccepted("{\"customerId\":\"c\"}"))
                .isInstanceOf(NonRetryableEventException.class);
        verify(processor, never()).applyWinFromQuote(any(), anyString());
        assertThat(count("failed")).as("counted per attempt").isEqualTo(2.0);
    }

    private double count(String outcome) {
        var counter = registry.find(QuoteAcceptedChannelMetrics.METER)
                .tags("channel", QuoteAcceptedChannelMetrics.KAFKA, "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }
}
