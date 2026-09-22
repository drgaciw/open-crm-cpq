package com.opencrm.common.event;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.time.Duration;

/**
 * Shared Kafka consumer error handling (ADR-018 D7). Spring Kafka's default handler retries a failing record a few
 * times and then logs and <em>skips</em> it, so a poison message was silently dropped. With this in place:
 * <ul>
 *   <li>a failing record is retried with bounded exponential backoff (default 4 retries, 1s doubling to 10s; the
 *       partition waits meanwhile, which keeps per-key order);</li>
 *   <li>then it is published to {@code <topic>.DLT} with Spring Kafka's exception and origin headers, and its offset
 *       is committed;</li>
 *   <li>{@link NonRetryableEventException} and Jackson parse errors go to the DLT at once, as do Spring Kafka's
 *       built-in non-retryable types (deserialization and conversion failures).</li>
 * </ul>
 * The DLT record goes to the partition the key hashes to rather than the original partition number, so a DLT topic
 * with fewer partitions still works. Boot's listener container factory picks up the {@link CommonErrorHandler} bean.
 * A service can replace it with its own bean, or switch it off with {@code opencrm.kafka.consumer.dead-letter.enabled=false}.
 *
 * <p>No {@code ErrorHandlingDeserializer}: every listener receives the raw {@code String} value and parses it itself,
 * so a deserializer cannot fail; parse errors surface in the listener and are classified above.
 */
@AutoConfiguration(afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@ConditionalOnClass(DefaultErrorHandler.class)
@ConditionalOnBean(KafkaOperations.class)
@ConditionalOnProperty(name = "opencrm.kafka.consumer.dead-letter.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KafkaConsumerErrorHandlingAutoConfiguration.RetryProperties.class)
@Slf4j
public class KafkaConsumerErrorHandlingAutoConfiguration {

    /** Suffix of the dead-letter topic; Spring Kafka's convention. */
    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public DefaultErrorHandler kafkaDeadLetterErrorHandler(KafkaOperations<?, ?> kafkaOperations, RetryProperties retry) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> {
                    log.error("Kafka record {}-{}@{} (key {}) failed after retries; publishing to {}{}",
                            record.topic(), record.partition(), record.offset(), record.key(), record.topic(),
                            DLT_SUFFIX, exception);
                    return new TopicPartition(record.topic() + DLT_SUFFIX, -1);
                });
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retry.getMaxRetries());
        backOff.setInitialInterval(retry.getInitialInterval().toMillis());
        backOff.setMultiplier(retry.getMultiplier());
        backOff.setMaxInterval(retry.getMaxInterval().toMillis());
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(NonRetryableEventException.class,
                com.fasterxml.jackson.core.JsonProcessingException.class);
        return handler;
    }

    /** {@code opencrm.kafka.consumer.retry.*}: in-place retry before a record goes to the DLT. */
    @Getter
    @Setter
    @ConfigurationProperties(prefix = "opencrm.kafka.consumer.retry")
    public static class RetryProperties {
        private int maxRetries = 4;
        private Duration initialInterval = Duration.ofSeconds(1);
        private double multiplier = 2.0;
        private Duration maxInterval = Duration.ofSeconds(10);
    }
}
