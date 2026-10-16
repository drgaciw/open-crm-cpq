package com.opencrm.quote.event;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.EventHeaders;
import com.opencrm.common.event.KafkaInbox;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.quote.client.SubscriptionScheduleClient;
import com.opencrm.quote.client.SubscriptionScheduleView;
import com.opencrm.quote.quote.DealType;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteContinuationService;
import com.opencrm.quote.quote.QuoteMapper;
import com.opencrm.quote.quote.QuoteNumberGenerator;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import com.opencrm.quote.ramp.RampEscalatorExpander;
import com.opencrm.quote.ramp.RampPricingConfiguration;
import com.opencrm.quote.ramp.RampScheduleAttacher;
import com.opencrm.quote.ramp.RampScheduleValidator;
import com.opencrm.security.CurrentActor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Re-delivery of one eligibility event, and a later republish with a new event id, create one renewal quote
 * and one commercial-decision audit row. Real PostgreSQL and Flyway (including V1_11), real inbox, real
 * continuation authoring. The Kafka container is not started; the listener method is invoked directly.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = SubscriptionRenewalEligibleReplayIntegrationTest.ReplayConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.default_schema=quote",
                "spring.flyway.enabled=true",
                "spring.flyway.schemas=quote",
                "spring.flyway.default-schema=quote",
                "opencrm.pricing.ramp.enabled=true",
                "opencrm.kafka.inbox.enabled=true",
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.bootstrap-servers=localhost:9092",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration"
        })
@DisplayName("Renewal eligibility re-delivery creates one draft quote")
class SubscriptionRenewalEligibleReplayIntegrationTest {

    private static final String PAYLOAD = """
            {"subscriptionId":"SUB-R3","renewalDate":"2029-12-01","currentPlan":"Growth","usageBand":"HIGH","ignoredFutureField":"kept"}""";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(256L * 1024 * 1024));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=quote");
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaAuditing
    @EntityScan(basePackages = "com.opencrm.quote")
    @EnableJpaRepositories(basePackages = {"com.opencrm.quote.quote", "com.opencrm.quote.lineitem", "com.opencrm.quote.ramp"})
    @Import({
            QuoteContinuationService.class,
            QuoteNumberGenerator.class,
            RampScheduleAttacher.class,
            RampScheduleValidator.class,
            RampEscalatorExpander.class,
            RampPricingConfiguration.class,
            SubscriptionRenewalEligibleEventConsumer.class
    })
    static class ReplayConfiguration {
        @Bean
        SubscriptionScheduleClient subscriptionScheduleClient() {
            return mock(SubscriptionScheduleClient.class);
        }

        @Bean
        QuoteMapper quoteMapper() {
            return Mappers.getMapper(QuoteMapper.class);
        }

        @Bean
        CurrentActor currentActor() {
            return new CurrentActor("service:quote-service", false);
        }

        @Bean
        @Qualifier("subscriptionScheduleRestClient")
        RestClient subscriptionScheduleRestClient() {
            return mock(RestClient.class);
        }

        @Bean
        KafkaInbox kafkaInbox(JdbcTemplate jdbc) {
            return new KafkaInbox(jdbc);
        }
    }

    @Autowired
    SubscriptionRenewalEligibleEventConsumer consumer;
    @Autowired
    SubscriptionScheduleClient schedules;
    @Autowired
    QuoteRepository quotes;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        when(schedules.byNumber("SUB-R3")).thenReturn(target());
        jdbc.update("DELETE FROM commercial_decision_audit");
        jdbc.update("DELETE FROM quote_ramp_steps");
        jdbc.update("DELETE FROM quote_line_items");
        jdbc.update("DELETE FROM quotes");
        jdbc.update("DELETE FROM processed_events");
    }

    @Test
    @DisplayName("the same record twice, then a nightly republish, leaves one quote, one audit row, and no outbox event")
    void redeliveryAndRepublishCreateOneQuote() {
        String eventId = UUID.randomUUID().toString();
        consumer.onRenewalEligible(record(eventId));
        consumer.onRenewalEligible(record(eventId));
        consumer.onRenewalEligible(record(UUID.randomUUID().toString()));

        List<Quote> renewals = quotes.findByDealTypeAndDeletedFalse(DealType.RENEWAL, PageRequest.of(0, 10)).getContent();
        assertThat(renewals).hasSize(1);
        Quote draft = renewals.getFirst();
        assertThat(draft.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(draft.getTargetSubscriptionNumber()).isEqualTo("SUB-R3");
        assertThat(draft.getStartDate()).isEqualTo(LocalDate.of(2030, 1, 1));
        assertThat(draft.getSubscriptionEffectiveDate()).isEqualTo(LocalDate.of(2030, 1, 1));
        assertThat(draft.getObservedRevision()).isEqualTo(7L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM quote_line_items WHERE quote_id = ? AND is_deleted = false",
                Integer.class, draft.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isZero();

        Map<String, Object> audit = jdbc.queryForMap("SELECT * FROM commercial_decision_audit");
        assertThat(audit.get("decision_kind")).isEqualTo("SYSTEM_RENEWAL_DRAFT");
        assertThat(audit.get("decided_by")).isEqualTo("service:quote-service");
        assertThat(audit.get("policy_id")).isEqualTo("QuoteContinuationPolicy");
        assertThat(audit.get("operation")).isEqualTo("RENEWAL");
        assertThat(audit.get("mode")).isEqualTo("RENEWAL_EXIT_RATE");
        assertThat(audit.get("subscription_id")).isEqualTo("SUB-R3");
        assertThat(((Number) audit.get("quote_id")).longValue()).isEqualTo(draft.getId());
        assertThat(audit.get("event_id")).isEqualTo(eventId);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE consumer_group = ?",
                Integer.class, SubscriptionRenewalEligibleEventConsumer.CONSUMER_GROUP)).isEqualTo(2);
    }

    private static ConsumerRecord<String, String> record(String eventId) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(KafkaTopics.SUBSCRIPTION_RENEWAL_ELIGIBLE, 0, 4L,
                "SUB-R3", PAYLOAD);
        record.headers().add(EventHeaders.EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8));
        record.headers().add(EventHeaders.EVENT_TYPE,
                EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1.eventType().getBytes(StandardCharsets.UTF_8));
        record.headers().add(EventHeaders.EVENT_VERSION, "1".getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private static SubscriptionScheduleView target() {
        SubscriptionScheduleView.Line source = SubscriptionScheduleView.Line.builder().acceptedLineUuid("source-line")
                .productExternalId("synthetic-product").productName("Seat").productType("SAAS_SEAT_NAMED")
                .lineItemType("SAAS_SEAT_NAMED").billingFrequency("MONTHLY").billingAnchorDate(LocalDate.of(2027, 1, 1))
                .provenance("ACCEPTED_PRICED").periods(new ArrayList<>()).build();
        for (int i = 0; i < 3; i++) {
            source.getPeriods().add(SubscriptionScheduleView.Period.builder().sequence(i + 1).startMonth(i * 12 + 1).months(12)
                    .quantity(50).startDate(LocalDate.of(2027 + i, 1, 1)).endDate(LocalDate.of(2027 + i, 12, 31))
                    .netUnitPrice(BigDecimal.valueOf(40 + i * 5L)).netMonthly(BigDecimal.valueOf(2000 + i * 250L))
                    .periodAmount(BigDecimal.valueOf(24000 + i * 3000L)).build());
        }
        return SubscriptionScheduleView.builder().subscriptionNumber("SUB-R3").customerId("synthetic-customer")
                .currencyCode("USD").status("ACTIVE").startDate(LocalDate.of(2027, 1, 1)).endDate(LocalDate.of(2029, 12, 31))
                .commercialRevision(7L).lines(List.of(source)).build();
    }
}
