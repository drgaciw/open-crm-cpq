package com.opencrm.document.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.document.DocumentGenerationApplication;
import com.opencrm.document.domain.JobStatus;
import com.opencrm.document.repository.DocumentGenerationJobRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.KafkaHeaders;
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

        // ADR-018 D6: generation starts after the listener commits, so the async worker always finds the job.
        awaitCompleted(idempotencyKey);
    }

    @Test
    @DisplayName("D7: a poison record goes to cpq.quote.status.accepted.DLT at once, and the partition moves on")
    void poisonRecordGoesToDeadLetterTopic() throws Exception {
        String key = "poison-" + UUID.randomUUID();

        Instant sent = Instant.now();
        send(key, "{not json");

        ConsumerRecord<String, String> dead = awaitRecord(KafkaTopics.QUOTE_STATUS_ACCEPTED + ".DLT", key);
        assertThat(dead.value()).isEqualTo("{not json");
        assertThat(header(dead, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(KafkaTopics.QUOTE_STATUS_ACCEPTED);
        // The listener's exception is wrapped in ListenerExecutionFailedException; the cause header names ours.
        assertThat(header(dead, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN)).endsWith("NonRetryableEventException");
        // Not retried: the default schedule (1s, 2s, 4s, 8s) alone would take 15s before the DLT publish.
        assertThat(Duration.between(sent, Instant.now())).isLessThan(Duration.ofSeconds(10));

        // Same key, so same partition: a valid event behind the poison record is still processed.
        String quoteId = "after-poison-" + UUID.randomUUID();
        send(key, objectMapper.writeValueAsString(
                new QuoteAcceptedEvent(quoteId, Instant.now(), "CUST-IT", new BigDecimal("10.00"), "USD")));
        awaitJob("quote-" + quoteId + "-pdf");
    }

    private static ConsumerRecord<String, String> awaitRecord(String topic, String key) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plusSeconds(60);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("no record with key " + key + " on " + topic + " within 60s");
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private void awaitCompleted(String idempotencyKey) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            boolean completed = jobRepository.findAll().stream()
                    .anyMatch(j -> idempotencyKey.equals(j.getIdempotencyKey()) && j.getStatus() == JobStatus.COMPLETED);
            if (completed) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("document job " + idempotencyKey + " did not complete within 30s");
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
