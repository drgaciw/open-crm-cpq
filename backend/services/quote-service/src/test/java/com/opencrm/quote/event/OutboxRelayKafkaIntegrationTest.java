package com.opencrm.quote.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.OutboxEventRepository;
import com.opencrm.common.event.OutboxRelayPublisher;
import com.opencrm.quote.QuoteServiceApplication;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * ADR-018 D1 end-to-end (producer side): an outbox row written in quote-service's own transaction is relayed to
 * Kafka by the auto-configured {@link OutboxRelayPublisher} and then marked published.
 *
 * <p>The full quote-service context runs against Testcontainers PostgreSQL (real Flyway migrations, including
 * {@code outbox_events}) and a real Kafka broker ({@code apache/kafka}), with {@code opencrm.outbox.relay.enabled=true}.
 */
@Tag("integration")
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(
        classes = QuoteServiceApplication.class,
        properties = {
            "opencrm.outbox.relay.enabled=true",
            // Mirrors application.yml; the Surefire system property would otherwise drop the Event Hubs Kafka exclude.
            "spring.autoconfigure.exclude="
                    + "com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration"
        })
@DisplayName("ADR-018 D1: quote-service outbox → Kafka (Testcontainers)")
// Close the context after this class: its scheduled relay would otherwise keep leasing rows in the shared test
// database (and retrying a stopped broker) while later integration tests run.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboxRelayKafkaIntegrationTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1");

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired OutboxEventPublisher publisher;
    @Autowired OutboxEventRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired OutboxRelayPublisher relay;

    @Test
    @DisplayName("committed outbox row reaches its Kafka topic and is marked published")
    void committedOutboxRowIsRelayedToKafka() {
        String aggregateId = "it-" + UUID.randomUUID();
        String topic = KafkaTopics.QUOTE_STATUS_ACCEPTED;

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                publisher.publish("Quote", aggregateId, topic, Map.of("quoteId", aggregateId, "currencyCode", "USD")));

        ConsumerRecord<String, String> record = awaitRecord(topic, aggregateId, Duration.ofSeconds(60));
        assertThat(record.value()).contains(aggregateId).contains("USD");

        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)
                && repository.findUnpublishedEvents().stream().anyMatch(e -> aggregateId.equals(e.getAggregateId()))) {
            sleep(500);
        }
        assertThat(repository.findUnpublishedEvents())
                .as("relay marks the row published after sending")
                .noneMatch(e -> aggregateId.equals(e.getAggregateId()));
    }

    private static ConsumerRecord<String, String> awaitRecord(String topic, String key, Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plus(timeout);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("no record with key " + key + " on " + topic + " within " + timeout);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
