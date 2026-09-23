package com.opencrm.crm.event;

import tools.jackson.databind.ObjectMapper;
import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Legacy channel: quote acceptance over Redis Pub/Sub {@code quote:accepted}.
 *
 * <p>ADR-018 D8 dual run: {@link QuoteAcceptedKafkaListener} consumes the same acceptances from Kafka, and both hand
 * them to {@link QuoteAcceptedDealProcessor}, which deduplicates in the deal transaction. Pub/Sub keeps nothing, so a
 * message sent while crm-service is down is lost; Kafka is the channel that survives that. This listener and the
 * quote-service publisher are removed once the dual run shows Kafka alone is enough.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuoteAcceptedEventListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final QuoteAcceptedDealProcessor quoteAcceptedDealProcessor;
    private final QuoteAcceptedChannelMetrics metrics;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            QuoteAcceptedCrmMessage event = objectMapper.readValue(body, QuoteAcceptedCrmMessage.class);

            String dealExternalId = event.getDealExternalId();
            if (dealExternalId == null || dealExternalId.isBlank()) {
                log.warn("QuoteAcceptedCrmMessage received without dealExternalId, skipping CRM update");
                metrics.record(QuoteAcceptedChannelMetrics.REDIS, QuoteAcceptedOutcome.NO_DEAL);
                return;
            }
            metrics.record(QuoteAcceptedChannelMetrics.REDIS,
                    quoteAcceptedDealProcessor.applyWinFromQuote(event, dealExternalId));
        } catch (Exception e) {
            metrics.record(QuoteAcceptedChannelMetrics.REDIS, QuoteAcceptedOutcome.FAILED);
            // Pub/Sub cannot redeliver; the Kafka channel is the retrying one during the dual run.
            log.error("Failed to process QuoteAcceptedCrmMessage from Redis", e);
        }
    }
}
