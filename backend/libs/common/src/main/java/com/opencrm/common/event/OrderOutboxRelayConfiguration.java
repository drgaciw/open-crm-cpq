package com.opencrm.common.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

/** Dedicated OMS delivery-lease transport. Disabled unless its existing OMS switch is enabled. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "oms.outbox-relay.enabled", havingValue = "true")
public class OrderOutboxRelayConfiguration {
    @Bean
    OrderOutboxRelay orderOutboxRelay(OutboxEventRepository repository,
            KafkaTemplate<String, String> kafka, OutboxDeliveryStore store) {
        return new OrderOutboxRelay(repository, kafka, store);
    }
}
