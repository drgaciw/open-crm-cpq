package com.opencrm.contract.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.acl.ErpAdapter;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Consumes quote accepted events and creates subscriptions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuoteAcceptedEventConsumer {
    
    private final SubscriptionRepository subscriptionRepository;
    private final ErpAdapter erpAdapter;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(topics = KafkaTopics.QUOTE_STATUS_ACCEPTED, groupId = "contract-asset-management")
    @Transactional
    public void handleQuoteAccepted(String message) {
        try {
            QuoteAcceptedEvent event = objectMapper.readValue(message, QuoteAcceptedEvent.class);
            
            log.info("Received quote accepted event for quote: {} (eventType={}, version={})",
                    event.getQuoteId(), event.getEventType(), event.getEventVersion());

            if (subscriptionRepository.existsByQuoteId(event.getQuoteId())) {
                log.info("Subscription already exists for quote: {}, skipping activation", event.getQuoteId());
                return;
            }

            LocalDate startDate = resolveStartDate(event);
            LocalDate endDate = resolveEndDate(event, startDate);

            Subscription subscription = Subscription.builder()
                    .subscriptionNumber(QuoteAcceptedSubscriptionIdempotency.subscriptionNumber(event))
                    .customerId(event.getCustomerId())
                    .quoteId(event.getQuoteId())
                    .status(SubscriptionStatus.PENDING)
                    .planName("Standard Plan")
                    .startDate(startDate)
                    .endDate(endDate)
                    .renewalDate(endDate.minusDays(30))
                    .arr(event.getTotalAmount())
                    .currencyCode(event.getCurrencyCode())
                    .build();
            
            Subscription saved = subscriptionRepository.save(subscription);
            
            // Sync to ERP via ACL with rate limiting
            try {
                String erpSubscriptionId = erpAdapter.syncToErp(saved);
                saved.setErpSubscriptionId(erpSubscriptionId);
                saved.setErpSyncStatus("SYNCED");
                subscriptionRepository.save(saved);
                
                // Activate subscription after successful ERP sync
                saved.activate();
                subscriptionRepository.save(saved);
                
            } catch (Exception e) {
                log.error("Failed to sync subscription to ERP", e);
                saved.setErpSyncStatus("FAILED");
                subscriptionRepository.save(saved);
            }
            
            log.info("Created subscription for quote: {}", event.getQuoteId());
            
        } catch (Exception e) {
            log.error("Error processing quote accepted event", e);
            throw new RuntimeException("Failed to process quote accepted event", e);
        }
    }

    private static LocalDate resolveStartDate(QuoteAcceptedEvent event) {
        if (event.getTerms() != null && event.getTerms().getStartDate() != null) {
            return LocalDate.parse(event.getTerms().getStartDate());
        }
        return LocalDate.now();
    }

    private static LocalDate resolveEndDate(QuoteAcceptedEvent event, LocalDate startDate) {
        if (event.getTerms() != null && event.getTerms().getEndDate() != null) {
            return LocalDate.parse(event.getTerms().getEndDate());
        }
        int months = event.getTerms() != null && event.getTerms().getTermMonths() != null
                ? event.getTerms().getTermMonths()
                : 12;
        return startDate.plusMonths(months);
    }
}
