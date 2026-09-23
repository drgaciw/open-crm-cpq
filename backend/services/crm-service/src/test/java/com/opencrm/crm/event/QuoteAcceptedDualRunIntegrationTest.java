package com.opencrm.crm.event;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.opencrm.common.event.EventHeaders;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.crm.BaseIntegrationTest;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.deal.DealStage;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * ADR-018 D8 dual run, end to end: crm-service takes quote acceptance from Kafka ({@code cpq.quote.status.accepted},
 * real {@code apache/kafka} broker) as well as Redis {@code quote:accepted}, and marks the deal WON once whichever
 * channel arrives first. Both claim the quote UUID in {@code processed_events} inside the deal transaction.
 */
@DisplayName("ADR-018 D8: quote acceptance over Kafka and Redis marks the deal WON once")
@Import(QuoteAcceptedDualRunIntegrationTest.Metrics.class)
class QuoteAcceptedDualRunIntegrationTest extends BaseIntegrationTest {

    /** A real registry, so the channel counters can be asserted. */
    @TestConfiguration
    static class Metrics {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("opencrm.crm.quote-accepted.kafka.enabled", () -> "true");
    }

    @Autowired DealRepository dealRepository;
    @Autowired StringRedisTemplate stringRedisTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MeterRegistry meterRegistry;

    private final Instant acceptedAt = Instant.parse("2026-09-22T12:00:00Z");
    private Deal deal;

    @BeforeEach
    void seedDeal() {
        deal = dealRepository.saveAndFlush(Deal.builder()
                .name("D8 dual-run deal")
                .stage(DealStage.PROPOSAL)
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .build());
        entityManager.clear();
    }

    @Test
    @DisplayName("Kafka alone: the acceptance marks the deal WON with amount, currency and wonAt")
    void kafkaMarksDealWon() throws Exception {
        String quoteUuid = "d8-" + UUID.randomUUID();

        sendKafka(quoteUuid, kafkaEvent(quoteUuid, "Q-D8-1"));

        Deal won = awaitWon();
        assertThat(won.getAmount()).isEqualByComparingTo("2500.00");
        assertThat(won.getCurrency()).isEqualTo("EUR");
        assertThat(won.getWonAt()).isEqualTo(acceptedAt);
        assertThat(claims(quoteUuid)).isEqualTo(1);
    }

    @Test
    @DisplayName("dual run: Redis first, then the same acceptance on Kafka, applies once")
    void sameAcceptanceOnBothChannelsAppliesOnce() throws Exception {
        String quoteUuid = "d8-" + UUID.randomUUID();
        QuoteAcceptedCrmMessage redisMessage = QuoteAcceptedCrmMessage.builder()
                .quoteUuid(quoteUuid).quoteNumber("Q-D8-2").dealExternalId(deal.getUuid())
                .totalAmount(new BigDecimal("2500.00")).currency("EUR").acceptedAt(acceptedAt).build();
        double redisApplied = count("redis", "applied");
        double kafkaDuplicates = count("kafka", "duplicate");

        stringRedisTemplate.convertAndSend(RedisSubscriberConfig.QUOTE_ACCEPTED_CHANNEL,
                objectMapper.writeValueAsString(redisMessage));
        Deal won = awaitWon();
        // Change the deal after the first apply, so a second apply would be visible.
        won.setAmount(new BigDecimal("999.99"));
        dealRepository.saveAndFlush(won);
        entityManager.clear();

        sendKafka(quoteUuid, kafkaEvent(quoteUuid, "Q-D8-2"));

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(6)).untilAsserted(() -> {
            entityManager.clear();
            Deal after = dealRepository.findByUuid(deal.getUuid()).orElseThrow();
            assertThat(after.getAmount()).as("the Kafka copy must not re-apply").isEqualByComparingTo("999.99");
            // The dual-run evidence (runbook): each acceptance is applied once and seen as a duplicate once.
            assertThat(count("redis", "applied") - redisApplied).isEqualTo(1.0);
            assertThat(count("kafka", "duplicate") - kafkaDuplicates).isEqualTo(1.0);
        });
        assertThat(claims(quoteUuid)).isEqualTo(1);
    }

    private String kafkaEvent(String quoteUuid, String quoteNumber) {
        QuoteAcceptedEvent event = QuoteAcceptedEvent.v1(
                QuoteAcceptedEvent.QuoteSnapshot.builder()
                        .quoteId(quoteUuid).quoteNumber(quoteNumber).acceptedAt(acceptedAt).build(),
                QuoteAcceptedEvent.CustomerSnapshot.builder().customerId("cust-d8").build(),
                QuoteAcceptedEvent.PricingSnapshot.builder()
                        .totalAmount(new BigDecimal("2500.00")).currencyCode("EUR").build(),
                null, null, null);
        event.setDeal(QuoteAcceptedEvent.DealSnapshot.builder().dealExternalId(deal.getUuid()).build());
        return objectMapper.writeValueAsString(event);
    }

    private void sendKafka(String key, String payload) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(KafkaTopics.QUOTE_STATUS_ACCEPTED, key, payload);
            record.headers().add(EventHeaders.EVENT_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            producer.send(record).get();
        }
    }

    private Deal awaitWon() {
        await().atMost(90, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            entityManager.clear();
            assertThat(dealRepository.findByUuid(deal.getUuid()).orElseThrow().getStage()).isEqualTo(DealStage.WON);
        });
        entityManager.clear();
        return dealRepository.findByUuid(deal.getUuid()).orElseThrow();
    }

    private double count(String channel, String outcome) {
        var counter = meterRegistry.find(QuoteAcceptedChannelMetrics.METER)
                .tags("channel", channel, "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    private int claims(String quoteUuid) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM processed_events WHERE consumer_group = ? AND event_id = ?",
                Integer.class, QuoteAcceptedIdempotency.CLAIM_NAMESPACE, quoteUuid);
        return count == null ? 0 : count;
    }
}
