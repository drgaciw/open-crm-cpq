package com.opencrm.document.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.document.DocumentGenerationApplication;
import com.opencrm.document.repository.DocumentGenerationJobRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * ADR-018 D1 end-to-end (consumer side): a {@code QuoteAcceptedEvent} on {@code cpq.quote.status.accepted} is
 * consumed by document-generation's real {@code @KafkaListener} and becomes an idempotent document job.
 *
 * <p>Full document-generation context against Testcontainers PostgreSQL (schema {@code document}, real Flyway
 * migrations) and Kafka ({@code apache/kafka}). Before ADR-018 D1 this context could not start at all
 * ({@code No bean named 'kafkaListenerContainerFactory'}).
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(
        classes = DocumentGenerationApplication.class,
        properties = {
            // Mirrors application.yml; the Surefire system property would otherwise drop the Event Hubs Kafka exclude.
            "spring.autoconfigure.exclude="
                    + "com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration"
        })
@DisplayName("ADR-018 D1: document-generation consumes QuoteAccepted from Kafka (Testcontainers)")
class QuoteAcceptedKafkaIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=document");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired DocumentGenerationJobRepository jobRepository;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("QuoteAccepted event creates exactly one document job, and a redelivery is ignored")
    void quoteAcceptedEventCreatesDocumentJob() throws Exception {
        String quoteId = "it-" + UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(
                new QuoteAcceptedEvent(quoteId, Instant.now(), "CUST-IT", new BigDecimal("1250.00"), "USD"));

        send(quoteId, payload);
        String idempotencyKey = "quote-" + quoteId + "-pdf";
        awaitJob(idempotencyKey);

        send(quoteId, payload); // at-least-once redelivery
        Thread.sleep(3000);
        assertThat(jobRepository.findAll().stream().filter(j -> idempotencyKey.equals(j.getIdempotencyKey())))
                .as("idempotent consumer: one job per quote")
                .hasSize(1);
    }

    private static void send(String key, String payload) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(KafkaTopics.QUOTE_STATUS_ACCEPTED, key, payload)).get();
        }
    }

    private void awaitJob(String idempotencyKey) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(90);
        while (Instant.now().isBefore(deadline)) {
            if (jobRepository.existsByIdempotencyKey(idempotencyKey)) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("no document job " + idempotencyKey + " within 90s");
    }
}
