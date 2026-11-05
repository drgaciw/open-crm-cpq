package com.opencrm.common.event;

import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import java.util.Map;
import java.util.Properties;

/**
 * Explicit opt-in factory policy; never changes an application's legacy listener factory.
 * Every failure, including a poison envelope, retains the partition offset until its cause is
 * corrected. One-second unlimited retry avoids Spring's default fatal/ten-attempt recovery.
 * This utility supplies no global error-handler bean and does not replace broker trust gates.
 */
public final class OmsKafkaListenerFactories {
    public static final String NAME="omsRetainingKafkaListenerContainerFactory";
    private OmsKafkaListenerFactories() {}
    public static ConcurrentKafkaListenerContainerFactory<Object,Object> retaining(ConsumerFactory<Object,Object> consumerFactory) {
        var factory=new ConcurrentKafkaListenerContainerFactory<Object,Object>();
        factory.setConsumerFactory(consumerFactory);
        var handler=new DefaultErrorHandler((record,failure)-> {
            throw new IllegalStateException("OMS delivery cannot be discarded",failure);
        },new FixedBackOff(1000L,FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.setClassifications(Map.of(),true);
        handler.setAckAfterHandle(false);
        handler.setCommitRecovered(false);
        factory.setCommonErrorHandler(handler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        // Per-container overrides preserve the shared consumer factory's broker/TLS configuration.
        // Never mutate its global configuration or allow auto-commit to bypass retained failures.
        var overrides=new Properties();
        overrides.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,"false");
        overrides.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class.getName());
        overrides.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class.getName());
        factory.setContainerCustomizer(container-> {
            var merged=new Properties();
            merged.putAll(container.getContainerProperties().getKafkaConsumerProperties());
            merged.putAll(overrides);
            container.getContainerProperties().setKafkaConsumerProperties(merged);
        });
        return factory;
    }
}
