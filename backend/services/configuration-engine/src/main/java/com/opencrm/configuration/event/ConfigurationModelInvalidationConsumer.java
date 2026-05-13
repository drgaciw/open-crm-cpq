package com.opencrm.configuration.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.configuration.model.ConfigurationModelCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigurationModelInvalidationConsumer {

    private final ConfigurationModelCache modelCache;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {
                    KafkaTopics.PRODUCT_CREATED,
                    KafkaTopics.PRODUCT_UPDATED,
                    KafkaTopics.PRODUCT_STATUS_RETIRED
            },
            groupId = "configuration-engine-model-cache"
    )
    public void handleProductChanged(String message) {
        Long productId = readRequiredLong(message, "productId");
        modelCache.invalidateProduct(productId);
    }

    @KafkaListener(
            topics = {
                    KafkaTopics.PRODUCT_BUNDLE_CREATED,
                    KafkaTopics.PRODUCT_BUNDLE_UPDATED,
                    KafkaTopics.PRODUCT_BUNDLE_DELETED
            },
            groupId = "configuration-engine-model-cache"
    )
    public void handleBundleChanged(String message) {
        Long bundleProductId = readRequiredLong(message, "productId");
        modelCache.invalidateBundle(bundleProductId);
    }

    private Long readRequiredLong(String message, String fieldName) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode value = root.get(fieldName);
            if (value == null || value.isNull()) {
                throw new IllegalArgumentException("Missing event field: " + fieldName);
            }
            if (value.isNumber()) {
                return value.longValue();
            }
            return Long.valueOf(value.asText());
        } catch (Exception e) {
            log.error("Failed to read {} from configuration model invalidation event", fieldName, e);
            throw new IllegalArgumentException("Invalid configuration model invalidation event", e);
        }
    }
}
