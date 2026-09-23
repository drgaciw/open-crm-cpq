package com.opencrm.common.event;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.Setter;
import org.apache.kafka.clients.admin.Admin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;

import java.time.Clock;
import java.time.Duration;

/**
 * Registers {@link DeadLetterMetrics} (ADR-018 D10) wherever the dead-letter handling of
 * {@link KafkaConsumerErrorHandlingAutoConfiguration} applies. Boot binds it to the {@code MeterRegistry} like any
 * {@code MeterBinder}; it adds a depth gauge per DLT once the listener containers are known. Consumer lag needs nothing
 * here: Boot's {@code KafkaMetricsAutoConfiguration} already binds the Kafka client metrics, including
 * {@code kafka.consumer.fetch.manager.records.lag.max}.
 */
@AutoConfiguration(afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@ConditionalOnClass({MeterRegistry.class, Admin.class, KafkaAdmin.class})
@ConditionalOnBean(KafkaAdmin.class)
@ConditionalOnProperty(name = "opencrm.kafka.consumer.dead-letter.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DeadLetterMetricsAutoConfiguration.DepthProperties.class)
public class DeadLetterMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DeadLetterMetrics deadLetterMetrics(KafkaAdmin kafkaAdmin,
                                               ObjectProvider<KafkaListenerEndpointRegistry> listeners,
                                               DepthProperties depth) {
        return new DeadLetterMetrics(kafkaAdmin, listeners, depth.getCacheTtl(), depth.getTimeout(),
                Clock.systemUTC());
    }

    /** {@code opencrm.kafka.consumer.dead-letter.depth.*}: how often, and how patiently, the depth gauge asks. */
    @Getter
    @Setter
    @ConfigurationProperties(prefix = "opencrm.kafka.consumer.dead-letter.depth")
    public static class DepthProperties {
        /** A gauge read younger than this serves the cached value. */
        private Duration cacheTtl = Duration.ofSeconds(30);
        /** Broker round-trip limit for one refresh. */
        private Duration timeout = Duration.ofSeconds(5);
    }
}
