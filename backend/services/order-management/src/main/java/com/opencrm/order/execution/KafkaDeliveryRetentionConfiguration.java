package com.opencrm.order.execution;

import com.opencrm.common.event.OmsKafkaListenerFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;

/**
 * Bounded delivery policy for order-management listeners: a failed record gets five deliveries,
 * one second apart, then goes to {@code <topic>.DLT} and its offset is committed so the partition continues.
 * Operators must work the DLT; logging a failure is never treated as successful consumption.
 * Boot applies this CommonErrorHandler bean to its auto-configured listener factory.
 */
@Configuration
public class KafkaDeliveryRetentionConfiguration {
    @Bean
    public DefaultErrorHandler kafkaDeliveryRetentionErrorHandler(KafkaOperations<?,?> kafkaTemplate) {
        return OmsKafkaListenerFactories.boundedDeadLetterErrorHandler(kafkaTemplate);
    }
}
