package com.opencrm.crm.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuoteAcceptedEventListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final QuoteAcceptedDealProcessor quoteAcceptedDealProcessor;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            QuoteAcceptedCrmMessage event = objectMapper.readValue(body, QuoteAcceptedCrmMessage.class);

            String dealExternalId = event.getDealExternalId();
            if (dealExternalId == null || dealExternalId.isBlank()) {
                log.warn("QuoteAcceptedCrmMessage received without dealExternalId, skipping CRM update");
                return;
            }

            String dedupeKey = QuoteAcceptedIdempotency.dedupeKey(event, dealExternalId);
            String redisKey = QuoteAcceptedIdempotency.redisKey(dedupeKey);
            Boolean first =
                    stringRedisTemplate.opsForValue().setIfAbsent(redisKey, "1", QuoteAcceptedIdempotency.TTL);
            if (Boolean.FALSE.equals(first)) {
                log.info("Duplicate quote acceptance for key {}, skipping", dedupeKey);
                return;
            }

            try {
                boolean applied = quoteAcceptedDealProcessor.applyWinFromQuote(event, dealExternalId);
                if (!applied) {
                    // Deal missing: release claim so a later republish can retry (no poisoned key).
                    stringRedisTemplate.delete(redisKey);
                }
            } catch (Exception e) {
                stringRedisTemplate.delete(redisKey);
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to process QuoteAcceptedCrmMessage", e);
        }
    }
}
