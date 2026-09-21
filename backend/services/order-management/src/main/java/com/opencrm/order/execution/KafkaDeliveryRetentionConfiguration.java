package com.opencrm.order.execution;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import java.util.Map;

/**
 * Retain every failed delivery, including poison envelopes, at its partition offset.
 * Operators must correct the cause or use a separately audited quarantine procedure;
 * this service never treats logging a failure as successful consumption.
 * A fixed one-second delay bounds retry pressure; deployment poll limits must exceed it.
 * Boot applies this CommonErrorHandler bean to its auto-configured listener factory.
 */
@Configuration
public class KafkaDeliveryRetentionConfiguration {
    @Bean
    public DefaultErrorHandler kafkaDeliveryRetentionErrorHandler() {
        var handler = new DefaultErrorHandler((record, exception) -> {
            throw new IllegalStateException("OMS delivery cannot be discarded", exception);
        }, new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        // Override Spring's default fatal classification as well as its ten-attempt limit.
        handler.setClassifications(Map.of(), true);
        handler.setAckAfterHandle(false);
        handler.setCommitRecovered(false);
        return handler;
    }
}
