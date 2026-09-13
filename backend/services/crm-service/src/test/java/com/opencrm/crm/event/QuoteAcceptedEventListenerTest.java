package com.opencrm.crm.event;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteAcceptedEventListenerTest {

    @Spy
    private ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

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
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
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

        String expectedKey = QuoteAcceptedIdempotency.redisKey("quote-uuid-1");
        when(valueOperations.setIfAbsent(eq(expectedKey), eq("1"), eq(QuoteAcceptedIdempotency.TTL)))
                .thenReturn(true);
        when(quoteAcceptedDealProcessor.applyWinFromQuote(any(QuoteAcceptedCrmMessage.class),
                eq("550e8400-e29b-41d4-a716-446655440001"))).thenReturn(true);

        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);

        listener.onMessage(message, null);

        verify(quoteAcceptedDealProcessor).applyWinFromQuote(any(QuoteAcceptedCrmMessage.class),
                eq("550e8400-e29b-41d4-a716-446655440001"));
        verify(stringRedisTemplate, never()).delete(any(String.class));
    }

    @Test
    void onMessage_skipsProcessingWhenDuplicate() throws Exception {
        QuoteAcceptedCrmMessage payload = QuoteAcceptedCrmMessage.builder()
                .dealExternalId("550e8400-e29b-41d4-a716-446655440002")
                .quoteUuid("quote-uuid-2")
                .build();
        byte[] body = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);

        String expectedKey = QuoteAcceptedIdempotency.redisKey("quote-uuid-2");
        when(valueOperations.setIfAbsent(eq(expectedKey), eq("1"), eq(QuoteAcceptedIdempotency.TTL)))
                .thenReturn(false);

        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);

        listener.onMessage(message, null);

        verify(quoteAcceptedDealProcessor, never()).applyWinFromQuote(any(), any());
        verify(stringRedisTemplate, never()).delete(any(String.class));
    }

    @Test
    void onMessage_skipsWhenDealExternalIdBlank() throws Exception {
        QuoteAcceptedCrmMessage payload = QuoteAcceptedCrmMessage.builder()
                .dealExternalId("   ")
                .quoteUuid("quote-uuid-blank")
                .build();
        byte[] body = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);

        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);

        listener.onMessage(message, null);

        verify(valueOperations, never()).setIfAbsent(any(), any(), any(Duration.class));
        verify(quoteAcceptedDealProcessor, never()).applyWinFromQuote(any(), any());
    }

    @Test
    void onMessage_deletesKeyWhenDealMissing() throws Exception {
        QuoteAcceptedCrmMessage payload = QuoteAcceptedCrmMessage.builder()
                .dealExternalId("550e8400-e29b-41d4-a716-446655440003")
                .quoteUuid("quote-uuid-missing-deal")
                .build();
        byte[] body = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);

        String expectedKey = QuoteAcceptedIdempotency.redisKey("quote-uuid-missing-deal");
        when(valueOperations.setIfAbsent(eq(expectedKey), eq("1"), eq(QuoteAcceptedIdempotency.TTL)))
                .thenReturn(true);
        when(quoteAcceptedDealProcessor.applyWinFromQuote(any(QuoteAcceptedCrmMessage.class),
                eq("550e8400-e29b-41d4-a716-446655440003"))).thenReturn(false);

        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);

        listener.onMessage(message, null);

        verify(stringRedisTemplate).delete(expectedKey);
    }

    @Test
    void onMessage_deletesKeyWhenProcessorThrows() throws Exception {
        QuoteAcceptedCrmMessage payload = QuoteAcceptedCrmMessage.builder()
                .dealExternalId("550e8400-e29b-41d4-a716-446655440004")
                .quoteUuid("quote-uuid-throw")
                .build();
        byte[] body = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);

        String expectedKey = QuoteAcceptedIdempotency.redisKey("quote-uuid-throw");
        when(valueOperations.setIfAbsent(eq(expectedKey), eq("1"), eq(QuoteAcceptedIdempotency.TTL)))
                .thenReturn(true);
        when(quoteAcceptedDealProcessor.applyWinFromQuote(any(QuoteAcceptedCrmMessage.class),
                eq("550e8400-e29b-41d4-a716-446655440004")))
                .thenThrow(new RuntimeException("db down"));

        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);

        listener.onMessage(message, null);

        verify(stringRedisTemplate).delete(expectedKey);
    }
}
