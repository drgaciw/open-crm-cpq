package com.opencrm.config.listener;

import com.opencrm.shared.kafka.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProductRetiredConsumer {

    @KafkaListener(topics = KafkaTopics.PRODUCT_STATUS_RETIRED)
    public void handleProductRetired(String payload) {
        log.info("Received Product Retired event: {}", payload);
        // Logic to invalidate or flag configurations using this product
    }
}
