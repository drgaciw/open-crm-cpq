package com.opencrm.crm.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteAcceptedEventListenerTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private QuoteAcceptedDealProcessor quoteAcceptedDealProcessor;

    @InjectMocks
    private QuoteAcceptedEventListener listener;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void onMessage_invokesProcessorWhenFirstDelivery() throws Exception {
        QuoteAcceptedCrmMessage payload = QuoteAcceptedCrmMessage.builder()
                .dealExternalId("550e8400-e29b-41d4-a716-446655440001")
                .quoteUuid("quote-uuid-1")
                .quoteNumber("Q-500")
                .totalAmount(new BigDecimal("99.00"))
                .currency("USD")
                .acceptedAt(Instant.parse("2025-05-02T10:00:00Z"))
                .build();
        byte[] body = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);

        when(valueOperations.setIfAbsent(any(String.class), eq("1"), any(Duration.class))).thenReturn(true);

        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);

        listener.onMessage(message, null);

        verify(quoteAcceptedDealProcessor).applyWinFromQuote(any(QuoteAcceptedCrmMessage.class),
                eq("550e8400-e29b-41d4-a716-446655440001"));
    }

    @Test
    void onMessage_skipsProcessingWhenDuplicate() throws Exception {
        QuoteAcceptedCrmMessage payload = QuoteAcceptedCrmMessage.builder()
                .dealExternalId("550e8400-e29b-41d4-a716-446655440002")
                .quoteUuid("quote-uuid-2")
                .build();
        byte[] body = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);

        when(valueOperations.setIfAbsent(any(String.class), eq("1"), any(Duration.class))).thenReturn(false);

        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);

        listener.onMessage(message, null);

        verify(quoteAcceptedDealProcessor, never()).applyWinFromQuote(any(), any());
    }
}
