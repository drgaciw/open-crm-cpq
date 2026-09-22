package com.opencrm.approval.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencrm.approval.ApprovalRoutingApplication;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.EventHeaders;
import com.opencrm.common.event.KafkaTopics;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * ADR-018 D6 redelivery proof for approval-routing, end to end: real {@code @KafkaListener}, real broker
 * ({@code apache/kafka}), real PostgreSQL with Flyway ({@code approval} schema, including {@code processed_events}).
 * Before D6, every delivery of {@code cpq.discount.approval.required} inserted another PENDING approval.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(
        classes = ApprovalRoutingApplication.class,
        properties = {
            // Mirrors application.yml; the Surefire system property would otherwise drop the Event Hubs Kafka exclude.
            "spring.autoconfigure.exclude="
                    + "com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration"
        })
@DisplayName("ADR-018 D6: approval-routing consumes each discount approval event once (Testcontainers)")
class DiscountApprovalKafkaIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=approval");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired ApprovalRequestRepository repository;
    @Autowired JdbcTemplate jdbc;

    private static String event(String quoteId, String marginDelta) {
        return """
                {"quoteId":"%s","lineId":"L-1","marginDelta":%s,"requestedDiscount":18.5,"currencyCode":"USD"}"""
                .formatted(quoteId, marginDelta);
    }

    @Test
    @DisplayName("the same event delivered twice (same eventId) creates one PENDING approval and one inbox claim")
    void sameEventIdIsProcessedOnce() throws Exception {
        String quoteId = "it-" + UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        send(quoteId, event(quoteId, "25"), eventId);
        awaitPending(quoteId, 1);
        send(quoteId, event(quoteId, "25"), eventId); // relay re-send after an ack timeout
        send(quoteId, event(quoteId, "40"), UUID.randomUUID().toString()); // a genuinely new event, as a marker
        awaitPending(quoteId, 2);

        List<ApprovalRequest> pending = pending(quoteId);
        assertThat(pending).extracting(r -> r.getMarginDelta().intValue()).containsExactlyInAnyOrder(25, 40);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE consumer_group = ? AND event_id = ?",
                Integer.class, DiscountApprovalEventConsumer.CONSUMER_GROUP, eventId)).isEqualTo(1);
    }

    @Test
    @DisplayName("an identical event without eventId (new offset) is caught by the natural-key backstop")
    void identicalEventWithoutEventIdIsADuplicate() throws Exception {
        String quoteId = "it-" + UUID.randomUUID();

        send(quoteId, event(quoteId, "25"), null);
        awaitPending(quoteId, 1);
        send(quoteId, event(quoteId, "25"), null);
        send(quoteId, event(quoteId, "40"), null); // marker: processed after the duplicate on the same partition
        awaitPending(quoteId, 2);

        assertThat(pending(quoteId)).extracting(r -> r.getMarginDelta().intValue()).containsExactlyInAnyOrder(25, 40);
    }

    private List<ApprovalRequest> pending(String quoteId) {
        return repository.findByQuoteIdAndStatus(quoteId, ApprovalStatus.PENDING);
    }

    private void awaitPending(String quoteId, int expected) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(90);
        while (Instant.now().isBefore(deadline)) {
            if (pending(quoteId).size() >= expected) {
                Thread.sleep(1000); // let any unexpected extra insert land before asserting
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("expected " + expected + " PENDING approvals for " + quoteId + " within 90s");
    }

    private static void send(String key, String payload, String eventId) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(KafkaTopics.DISCOUNT_APPROVAL_REQUIRED, key, payload);
            if (eventId != null) {
                record.headers().add(EventHeaders.EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8));
            }
            producer.send(record).get();
        }
    }
}
