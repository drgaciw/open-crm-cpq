package com.opencrm.document.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.DeadLetterCountingRecoverer;
import com.opencrm.common.event.DeadLetterMetrics;
import com.opencrm.common.event.DeadLetterTool;
import com.opencrm.common.event.DeadLetterTriage;
import com.opencrm.common.event.EventHeaders;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.document.DocumentGenerationApplication;
import com.opencrm.document.repository.DocumentGenerationJobRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.CommonClientConfigs;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * ADR-018 D7 and D10 against a real broker: a poison record is counted and shows up as DLT depth, and
 * {@link DeadLetterTool} triages it (peek, skip). A record that failed for a reason since fixed is replayed to its
 * original topic, processed by the real listener, and leaves the DLT at depth zero. Consumer lag is bound by Boot.
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
                    + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration",
            // Read the depth on every gauge read, so the assertions see each change at once.
            "opencrm.kafka.consumer.dead-letter.depth.cache-ttl=0s"
        })
@Import(DeadLetterTriageIntegrationTest.Metrics.class)
@DisplayName("ADR-018 D7/D10: dead letters are counted, measured, and triaged with DeadLetterTool (Testcontainers)")
class DeadLetterTriageIntegrationTest {

    private static final String TOPIC = KafkaTopics.QUOTE_STATUS_ACCEPTED;
    private static final String DLT = TOPIC + ".DLT";

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

    /** A real registry, so meter values can be asserted (Boot's test defaults would otherwise leave none). */
    @TestConfiguration
    static class Metrics {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired DocumentGenerationJobRepository jobRepository;
    @Autowired ObjectMapper objectMapper;
    @Autowired MeterRegistry meterRegistry;

    @Test
    @DisplayName("poison: counted, DLT depth 1, peeked and skipped to depth 0; a fixed failure is replayed and processed")
    void deadLettersAreCountedMeasuredAndTriaged() throws Exception {
        // 1. A poison record is dead-lettered by the real error handler.
        String poisonKey = "poison-" + UUID.randomUUID();
        send(TOPIC, poisonKey, "{not json", Map.of());
        awaitDepth(1);

        assertThat(meterRegistry.find(DeadLetterCountingRecoverer.METER)
                .tags("topic", TOPIC, "group", "document-generation", "exception", "NonRetryableEventException")
                .counter().count()).isEqualTo(1.0);
        assertThat(depthGauge()).isEqualTo(1.0);
        assertThat(meterRegistry.find("kafka.consumer.fetch.manager.records.lag.max").meters())
                .as("Boot binds the Kafka consumer metrics, consumer lag included").isNotEmpty();

        // 2. Peek shows what failed and for whom, and changes nothing.
        String peek = tool("peek", TOPIC);
        assertThat(peek).contains("key=" + poisonKey, "group=document-generation", "NonRetryableEventException");
        assertThat(total()).isEqualTo(1);

        // 3. Skip: the poison record must never be processed.
        assertThat(tool("skip", TOPIC, "--max", "1")).contains("skipped 1 record(s); 0 untriaged remain");
        assertThat(depthGauge()).isZero();

        // 4. A record that failed while, say, the database was down; the cause is now fixed.
        String quoteId = "replayed-" + UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        send(DLT, quoteId, objectMapper.writeValueAsString(
                        new QuoteAcceptedEvent(quoteId, Instant.now(), "CUST-IT", new BigDecimal("42.00"), "USD")),
                Map.of(KafkaHeaders.DLT_ORIGINAL_TOPIC, TOPIC,
                        KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, "document-generation",
                        KafkaHeaders.DLT_EXCEPTION_MESSAGE, "database was down",
                        EventHeaders.EVENT_ID, eventId));
        awaitDepth(1);

        assertThat(tool("replay", TOPIC, "--dry-run")).contains("DRY RUN: would replay 1 record(s)");
        assertThat(total()).as("a dry run changes nothing").isEqualTo(1);

        assertThat(tool("replay", TOPIC)).contains("replayed 1 record(s) to their original topic; 0 untriaged remain");
        awaitJob("quote-" + quoteId + "-pdf");
        assertThat(depthGauge()).isZero();

        ConsumerRecord<String, String> replayed = awaitRecord(TOPIC, quoteId);
        assertThat(header(replayed, EventHeaders.EVENT_ID)).as("the envelope survives the replay").isEqualTo(eventId);
        assertThat(StreamSupport.stream(replayed.headers().spliterator(), false).map(Header::key))
                .as("Spring Kafka's DLT headers are not replayed")
                .noneMatch(key -> key.startsWith("kafka_dlt-"));
    }

    private String tool(String... args) {
        String[] withBroker = java.util.stream.Stream.concat(java.util.Arrays.stream(args),
                java.util.stream.Stream.of("--bootstrap-servers", KAFKA.getBootstrapServers())).toArray(String[]::new);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = DeadLetterTool.run(withBroker, Map.of(), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        assertThat(exit).as("DeadLetterTool %s: %s", String.join(" ", args), err.toString(StandardCharsets.UTF_8))
                .isZero();
        return out.toString(StandardCharsets.UTF_8);
    }

    private double depthGauge() {
        return meterRegistry.find(DeadLetterMetrics.DEPTH_METER).tag("topic", DLT).gauge().value();
    }

    private long total() throws Exception {
        return new DeadLetterTriage(Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()),
                Duration.ofSeconds(30)).depth(DLT).values().stream().mapToLong(Long::longValue).sum();
    }

    private void awaitDepth(long expected) throws Exception {
        Instant deadline = Instant.now().plusSeconds(60);
        while (Instant.now().isBefore(deadline)) {
            if (total() == expected) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("DLT depth did not reach " + expected + " within 60s (now " + total() + ")");
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

    private static void send(String topic, String key, String payload, Map<String, String> headers) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
            headers.forEach((name, value) -> record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)));
            producer.send(record).get();
        }
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
}
