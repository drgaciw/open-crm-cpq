package com.opencrm.crm.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.deal.DealStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuoteAcceptedEventListener implements MessageListener {

    private final DealRepository dealRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            JsonNode event = objectMapper.readTree(body);

            String dealExternalId = event.has("dealExternalId") ? event.get("dealExternalId").asText(null) : null;
            if (dealExternalId == null || dealExternalId.isEmpty()) {
                log.warn("QuoteAcceptedEvent received without dealExternalId, skipping CRM update");
                return;
            }

            dealRepository.findByUuid(dealExternalId).ifPresentOrElse(
                    deal -> {
                        BigDecimal totalAmount = event.has("totalAmount") && !event.get("totalAmount").isNull()
                                ? new BigDecimal(event.get("totalAmount").asText()) : null;

                        deal.setStage(DealStage.WON);
                        if (totalAmount != null) {
                            deal.setAmount(totalAmount);
                        }
                        dealRepository.save(deal);
                        log.info("Updated deal {} to WON with amount {} from quote {}",
                                dealExternalId, totalAmount, event.get("quoteNumber").asText());
                    },
                    () -> log.warn("Deal with UUID {} not found for QuoteAcceptedEvent", dealExternalId)
            );
        } catch (Exception e) {
            log.error("Failed to process QuoteAcceptedEvent", e);
        }
    }
}
