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
            
            log.info("Received quote accepted event for quote: {}", event.getQuoteId());
            
            // Create subscription
            Subscription subscription = Subscription.builder()
                    .subscriptionNumber("SUB-" + event.getQuoteId())
                    .customerId(event.getCustomerId())
                    .quoteId(event.getQuoteId())
                    .status(SubscriptionStatus.PENDING)
                    .planName("Standard Plan") // In production, extract from quote
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusYears(1))
                    .renewalDate(LocalDate.now().plusYears(1).minusDays(30))
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
}
