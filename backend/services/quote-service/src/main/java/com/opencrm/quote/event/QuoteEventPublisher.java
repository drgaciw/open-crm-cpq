package com.opencrm.quote.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuoteEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public static final String QUOTE_ACCEPTED_CHANNEL = "quote:accepted";

    public void publishQuoteAccepted(QuoteAcceptedEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(QUOTE_ACCEPTED_CHANNEL, message);
            log.info("Published QuoteAcceptedEvent for quote: {}", event.getQuoteNumber());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize QuoteAcceptedEvent for quote: {}", event.getQuoteNumber(), e);
            throw new BusinessException("Failed to publish quote accepted event: serialization error", "EVENT_PUBLISH_FAILED");
        } catch (Exception e) {
            log.error("Failed to publish QuoteAcceptedEvent for quote: {}", event.getQuoteNumber(), e);
            throw new BusinessException("Failed to publish quote accepted event", "EVENT_PUBLISH_FAILED");
        }
    }
}
