package com.opencrm.configuration.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.ProductRetiredEvent;
import com.opencrm.configuration.model.ConfigurationModelCache;
import com.opencrm.configuration.session.ConfigurationSessionRepository;
import com.opencrm.configuration.session.ConfigurationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes product retired events and invalidates related configuration sessions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductRetiredEventConsumer {
    
    private final ConfigurationSessionRepository sessionRepository;
    private final ConfigurationModelCache configurationModelCache;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(topics = KafkaTopics.PRODUCT_STATUS_RETIRED, groupId = "configuration-engine")
    @Transactional
    public void handleProductRetired(String message) {
        try {
            ProductRetiredEvent event = objectMapper.readValue(message, ProductRetiredEvent.class);
            
            log.info("Received product retired event for product: {}", event.getProductId());
            configurationModelCache.invalidate(event.getProductId());

            // Find all active configuration sessions for this product
            var sessions = sessionRepository.findByProductIdAndStatus(
                    event.getProductId(), 
                    ConfigurationStatus.IN_PROGRESS
            );
            
            // Cancel all active sessions
            sessions.forEach(session -> {
                session.setStatus(ConfigurationStatus.CANCELLED);
                session.setValidationErrors("Product has been retired: " + event.getReasonCode());
            });
            sessionRepository.saveAll(sessions);
            
            log.info("Cancelled {} configuration sessions for retired product {}", 
                     sessions.size(), event.getProductId());
            
        } catch (Exception e) {
            log.error("Error processing product retired event", e);
            throw new RuntimeException("Failed to process product retired event", e);
        }
    }
}
