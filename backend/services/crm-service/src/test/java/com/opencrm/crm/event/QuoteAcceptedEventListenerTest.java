package com.opencrm.crm.event;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Redis {@code quote:accepted} listener: parses and delegates; deduplication lives in the processor (ADR-018 D8). */
@ExtendWith(MockitoExtension.class)
class QuoteAcceptedEventListenerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Mock
    private QuoteAcceptedDealProcessor quoteAcceptedDealProcessor;

    private QuoteAcceptedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new QuoteAcceptedEventListener(objectMapper, quoteAcceptedDealProcessor);
    }

    private Message message(String dealExternalId) throws Exception {
        QuoteAcceptedCrmMessage event = QuoteAcceptedCrmMessage.builder()
                .quoteNumber("Q-7")
                .quoteUuid("quote-uuid-7")
                .dealExternalId(dealExternalId)
                .totalAmount(new BigDecimal("900.00"))
                .currency("USD")
                .acceptedAt(Instant.parse("2026-09-22T12:00:00Z"))
                .build();
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(objectMapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8));
        return message;
    }

    @Test
    @DisplayName("hands the acceptance to the processor with the deal id")
    void onMessage_delegatesToProcessor() throws Exception {
        listener.onMessage(message("deal-7"), null);

        verify(quoteAcceptedDealProcessor).applyWinFromQuote(
                argThat(m -> "quote-uuid-7".equals(m.getQuoteUuid())), eq("deal-7"));
    }

    @Test
    @DisplayName("skips a message without a deal")
    void onMessage_skipsWhenDealExternalIdBlank() throws Exception {
        listener.onMessage(message(" "), null);

        verify(quoteAcceptedDealProcessor, never()).applyWinFromQuote(any(), anyString());
    }

    @Test
    @DisplayName("a processing failure is logged, not thrown (Pub/Sub cannot redeliver)")
    void onMessage_swallowsProcessorFailure() throws Exception {
        Message message = message("deal-7");
        when(quoteAcceptedDealProcessor.applyWinFromQuote(any(), anyString())).thenThrow(new IllegalStateException("db"));

        assertThatCode(() -> listener.onMessage(message, null)).doesNotThrowAnyException();
    }
}
