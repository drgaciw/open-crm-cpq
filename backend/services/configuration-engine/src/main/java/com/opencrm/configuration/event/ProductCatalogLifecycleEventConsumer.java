package com.opencrm.configuration.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.ProductCatalogLifecycleEvent;
import com.opencrm.configuration.model.ConfigurationModelCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Invalidates compiled configuration models when catalog or bundle structure changes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductCatalogLifecycleEventConsumer {

    private final ConfigurationModelCache configurationModelCache;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.PRODUCT_UPDATED, groupId = "configuration-engine-catalog")
    public void handleProductUpdated(String message) {
        handleCatalogChange(message, "product-updated");
    }

    @KafkaListener(topics = KafkaTopics.BUNDLE_STRUCTURE_CHANGED, groupId = "configuration-engine-catalog")
    public void handleBundleStructureChanged(String message) {
        handleCatalogChange(message, "bundle-structure-changed");
    }

    private void handleCatalogChange(String message, String source) {
        try {
            ProductCatalogLifecycleEvent event = objectMapper.readValue(message, ProductCatalogLifecycleEvent.class);
            configurationModelCache.invalidate(event.getAggregateId());
            log.info("Invalidated configuration model cache for {} due to {}", event.getAggregateId(), source);
        } catch (Exception e) {
            log.error("Failed to process catalog lifecycle event from {}", source, e);
            throw new RuntimeException("Failed to process catalog lifecycle event", e);
        }
    }
}
