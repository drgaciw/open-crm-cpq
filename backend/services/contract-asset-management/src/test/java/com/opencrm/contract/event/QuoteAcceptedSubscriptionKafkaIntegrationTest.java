package com.opencrm.contract.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencrm.common.event.EventHeaders;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.domain.ErpSyncStatus;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;
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
 * ADR-018 D6 end to end for contract-asset-management: real listener, broker and PostgreSQL ({@code contract} schema).
 * A redelivered quote-accepted event used to hit {@code UNIQUE(subscription_number)} inside the listener, next to an
 * ERP call. Now: one subscription, created with the ERP sync queued, then synced and activated by {@code ErpSyncJob}.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(
        classes = ContractAssetManagementApplication.class,
        properties = {
            "spring.autoconfigure.exclude="
                    + "com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration",
            "opencrm.erp-sync.initial-delay=PT1S",
            "opencrm.erp-sync.poll-interval=PT1S"
        })
@DisplayName("ADR-018 D6: contract-asset creates one subscription per accepted quote and syncs ERP after commit")
class QuoteAcceptedSubscriptionKafkaIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=contract");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired SubscriptionRepository repository;

    @Test
    @DisplayName("redelivery (same eventId, then no eventId) yields one subscription, SYNCED to ERP and ACTIVE")
    void redeliveryYieldsOneSyncedSubscription() throws Exception {
        String quoteId = "it-" + UUID.randomUUID();
        String payload = """
                {"quoteId":"%s","customerId":"C-IT","totalAmount":1200.00,"currencyCode":"EUR"}""".formatted(quoteId);
        String eventId = UUID.randomUUID().toString();

        send(quoteId, payload, eventId);
        send(quoteId, payload, eventId);
        send(quoteId, payload, null);

        Subscription synced = await(quoteId, s -> ErpSyncStatus.SYNCED.equals(s.getErpSyncStatus()));
        assertThat(synced.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(synced.getErpSubscriptionId()).isEqualTo("ERP-SUB-" + quoteId);
        Thread.sleep(2000);
        assertThat(subscriptions(quoteId)).hasSize(1);
    }

    private List<Subscription> subscriptions(String quoteId) {
        return repository.findAll().stream().filter(s -> quoteId.equals(s.getQuoteId())).toList();
    }

    private Subscription await(String quoteId, Predicate<Subscription> condition) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(90);
        while (Instant.now().isBefore(deadline)) {
            for (Subscription s : subscriptions(quoteId)) {
                if (condition.test(s)) {
                    return s;
                }
            }
            Thread.sleep(500);
        }
        throw new AssertionError("subscription for " + quoteId + " did not reach the expected state within 90s: "
                + subscriptions(quoteId));
    }

    private static void send(String key, String payload, String eventId) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(KafkaTopics.QUOTE_STATUS_ACCEPTED, key, payload);
            if (eventId != null) {
                record.headers().add(EventHeaders.EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8));
            }
            producer.send(record).get();
        }
    }
}
