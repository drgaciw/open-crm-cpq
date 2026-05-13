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
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final String TRACE_ID_KEY = "traceId";
    
    private final SubscriptionRepository subscriptionRepository;
    private final ErpAdapter erpAdapter;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(topics = KafkaTopics.QUOTE_STATUS_ACCEPTED, groupId = "contract-asset-management")
    @Transactional
    public void handleQuoteAccepted(String message) {
        try {
            QuoteAcceptedEvent event = objectMapper.readValue(message, QuoteAcceptedEvent.class);
            String traceId = correlationId(event);
            
            try (MDC.MDCCloseable ignored = MDC.putCloseable(TRACE_ID_KEY, traceId)) {
                createActivation(event, traceId);
            }
        } catch (Exception e) {
            log.error("Error processing quote accepted event", e);
            throw new RuntimeException("Failed to process quote accepted event", e);
        }
    }

    private void createActivation(QuoteAcceptedEvent event, String traceId) {
        log.info("Received quote accepted event for quote: {} [traceId={}]", event.getQuoteId(), traceId);

        String subscriptionNumber = subscriptionNumber(event);

        if (subscriptionRepository.existsBySubscriptionNumber(subscriptionNumber)) {
            log.info("Subscription activation already exists for quote: {} [traceId={}]", event.getQuoteId(), traceId);
            return;
        }
            
        Subscription subscription = Subscription.builder()
                .subscriptionNumber(subscriptionNumber)
                .customerId(event.getCustomerId())
                .quoteId(event.getQuoteId())
                .status(SubscriptionStatus.PENDING)
                .planName("Standard Plan")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusYears(1))
                .renewalDate(LocalDate.now().plusYears(1).minusDays(30))
                .arr(event.getTotalAmount())
                .currencyCode(event.getCurrencyCode())
                .build();
            
        Subscription saved;
        try {
            saved = subscriptionRepository.saveAndFlush(subscription);
        } catch (DataIntegrityViolationException e) {
            if (subscriptionRepository.existsBySubscriptionNumber(subscriptionNumber)) {
                log.info("Subscription activation already exists for quote: {} [traceId={}]", event.getQuoteId(), traceId);
                return;
            }
            throw e;
        }
            
        try {
            String erpSubscriptionId = erpAdapter.syncToErp(saved);
            saved.setErpSubscriptionId(erpSubscriptionId);
            saved.setErpSyncStatus("SYNCED");
                
            saved.activate();
            subscriptionRepository.save(saved);
                
        } catch (Exception e) {
            log.error("Failed to sync subscription to ERP [traceId={}]", traceId, e);
            saved.setErpSyncStatus("FAILED");
            subscriptionRepository.save(saved);
        }

        log.info("Created subscription for quote: {} [traceId={}]", event.getQuoteId(), traceId);
    }

    private static String subscriptionNumber(QuoteAcceptedEvent event) {
        return "SUB-" + event.getQuoteId();
    }

    private static String correlationId(QuoteAcceptedEvent event) {
        return "quote-accepted:" + event.getQuoteId();
    }
}
