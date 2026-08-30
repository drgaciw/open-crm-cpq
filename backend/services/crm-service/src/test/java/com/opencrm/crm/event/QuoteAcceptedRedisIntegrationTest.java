package com.opencrm.crm.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import com.opencrm.crm.BaseIntegrationTest;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.deal.DealStage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Redis pub/sub integration: quote-accepted → deal WON + duplicate-message idempotency.
 * <p>
 * Requires Docker (Testcontainers Postgres via {@link BaseIntegrationTest} + Redis).
 * Gated behind {@code @Tag("integration")} / {@code -Pintegration-tests}; unit profile stays Docker-free.
 * <p>
 * Deal-missing key poisoning (scout G3) is owned by B0-2 / PR-A; this IT covers the happy path
 * and post-success duplicate skip expected by PR-B.
 */
@DisplayName("Quote accepted Redis → deal WON integration")
@Tag("integration")
@Tag("docker")
class QuoteAcceptedRedisIntegrationTest extends BaseIntegrationTest {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
    }

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    private Deal seededDeal;
    private Instant acceptedAt;

    @BeforeEach
    void seedDealAndFlushRedis() {
        var keys = stringRedisTemplate.keys(QuoteAcceptedIdempotency.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }

        acceptedAt = Instant.parse("2025-06-15T14:30:00Z");
        seededDeal = dealRepository.saveAndFlush(Deal.builder()
                .name("Quote-accepted IT deal")
                .stage(DealStage.PROPOSAL)
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .build());
        entityManager.clear();
    }

    @Test
    @DisplayName("Publishing QuoteAcceptedCrmMessage marks deal WON with amount/currency/wonAt")
    void publish_marksDealWon() throws Exception {
        QuoteAcceptedCrmMessage message = sampleMessage(seededDeal.getUuid(), "quote-uuid-it-1", "Q-IT-1");

        stringRedisTemplate.convertAndSend(
                RedisSubscriberConfig.QUOTE_ACCEPTED_CHANNEL,
                objectMapper.writeValueAsString(message));

        Deal won = awaitDealWon(seededDeal.getUuid());

        assertThat(won.getStage()).isEqualTo(DealStage.WON);
        assertThat(won.getAmount()).isEqualByComparingTo("25000.50");
        assertThat(won.getCurrency()).isEqualTo("EUR");
        assertThat(won.getWonAt()).isEqualTo(acceptedAt);

        String redisKey = QuoteAcceptedIdempotency.redisKey(
                QuoteAcceptedIdempotency.dedupeKey(message, seededDeal.getUuid()));
        assertThat(Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey))).isTrue();
    }

    @Test
    @DisplayName("Duplicate identical message does not re-apply; idempotency key remains")
    void publish_duplicate_isIdempotent() throws Exception {
        QuoteAcceptedCrmMessage message = sampleMessage(seededDeal.getUuid(), "quote-uuid-it-2", "Q-IT-2");
        String payload = objectMapper.writeValueAsString(message);

        stringRedisTemplate.convertAndSend(RedisSubscriberConfig.QUOTE_ACCEPTED_CHANNEL, payload);
        Deal won = awaitDealWon(seededDeal.getUuid());
        Long versionAfterFirst = won.getVersion();
        Instant updatedAfterFirst = won.getUpdatedAt();

        // Mutate after successful apply so a second apply would be observable.
        won.setAmount(new BigDecimal("999.99"));
        dealRepository.saveAndFlush(won);
        entityManager.clear();

        stringRedisTemplate.convertAndSend(RedisSubscriberConfig.QUOTE_ACCEPTED_CHANNEL, payload);

        // Hold long enough that a non-idempotent second apply would overwrite amount.
        await().pollDelay(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(4))
                .untilAsserted(() -> {
                    entityManager.clear();
                    Deal afterDup = dealRepository.findByUuid(seededDeal.getUuid()).orElseThrow();
                    assertThat(afterDup.getAmount())
                            .as("duplicate delivery must not re-apply quote totals")
                            .isEqualByComparingTo("999.99");
                    assertThat(afterDup.getCurrency()).isEqualTo("EUR");
                    assertThat(afterDup.getStage()).isEqualTo(DealStage.WON);
                    // +1 from the intentional post-win mutation only; duplicate must not save again
                    assertThat(afterDup.getVersion()).isEqualTo(versionAfterFirst + 1);
                    assertThat(afterDup.getUpdatedAt()).isAfterOrEqualTo(updatedAfterFirst);
                });

        String redisKey = QuoteAcceptedIdempotency.redisKey(
                QuoteAcceptedIdempotency.dedupeKey(message, seededDeal.getUuid()));
        assertThat(Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey))).isTrue();
        assertThat(stringRedisTemplate.opsForValue().get(redisKey)).isEqualTo("1");
    }

    private QuoteAcceptedCrmMessage sampleMessage(String dealUuid, String quoteUuid, String quoteNumber) {
        return QuoteAcceptedCrmMessage.builder()
                .dealExternalId(dealUuid)
                .quoteUuid(quoteUuid)
                .quoteNumber(quoteNumber)
                .totalAmount(new BigDecimal("25000.50"))
                .currency("EUR")
                .acceptedAt(acceptedAt)
                .build();
    }

    private Deal awaitDealWon(String dealUuid) {
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    entityManager.clear();
                    Deal deal = dealRepository.findByUuid(dealUuid).orElseThrow();
                    assertThat(deal.getStage()).isEqualTo(DealStage.WON);
                });
        entityManager.clear();
        return dealRepository.findByUuid(dealUuid).orElseThrow();
    }
}
