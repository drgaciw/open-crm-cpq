package com.opencrm.contract.asset;

import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.service.AcceptedQuoteSubscriptionService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The legacy acceptance hook must not publish active assets before real activation. */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test", "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false", "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false"})
class AssetWriteHoldIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String url = POSTGRES.getJdbcUrl();
            return url + (url.contains("?") ? "&" : "?") + "currentSchema=contract";
        });
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AcceptedQuoteSubscriptionService subscriptions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void defaultHoldStillProcessesAndDeduplicatesAcceptanceWithoutAssetsOrEvents() throws Exception {
        QuoteAcceptedEvent event = AssetActivationTestSupport.twoLines(AssetActivationTestSupport.fixture());
        String quoteId = "it-hold-" + UUID.randomUUID();
        event.setQuoteId(quoteId);
        event.getQuote().setQuoteId(quoteId);

        subscriptions.apply(event);
        subscriptions.apply(event);

        Long subscriptionId = jdbc.queryForObject("select id from subscriptions where subscription_number = ?",
                Long.class, "SUB-" + quoteId);
        assertThat(jdbc.queryForObject("select status from subscriptions where id = ?", String.class, subscriptionId))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from subscription_rate_periods where subscription_id = ?",
                Long.class, subscriptionId)).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from applied_quote_acceptances where accepted_quote_id = ?",
                Long.class, quoteId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from assets where subscription_id = ?",
                Long.class, subscriptionId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where topic in (?, ?)",
                Long.class, KafkaTopics.ASSET_CREATED, KafkaTopics.ASSET_AUDIT)).isZero();
    }

    @Test
    void defaultHoldAlsoLeavesAmendmentMutationWithoutAssetsOrEvents() throws Exception {
        QuoteAcceptedEvent sale = AssetActivationTestSupport.twoLines(AssetActivationTestSupport.fixture());
        String quoteId = "it-hold-sale-" + UUID.randomUUID();
        sale.setQuoteId(quoteId);
        sale.getQuote().setQuoteId(quoteId);
        subscriptions.apply(sale);
        Long subscriptionId = jdbc.queryForObject("select id from subscriptions where subscription_number = ?",
                Long.class, "SUB-" + quoteId);
        jdbc.update("update subscriptions set status = 'ACTIVE' where id = ?", subscriptionId);

        QuoteAcceptedEvent amendment = AssetActivationTestSupport.fixture();
        String amendmentId = "it-hold-amend-" + UUID.randomUUID();
        amendment.setQuoteId(amendmentId);
        amendment.getQuote().setQuoteId(amendmentId);
        amendment.setCustomerId(sale.getCustomerId());
        amendment.getCustomer().setCustomerId(sale.getCustomerId());
        amendment.getDeal().setDealType("AMENDMENT");
        amendment.setSubscriptionChange(QuoteAcceptedEvent.SubscriptionChangeSnapshot.builder()
                .targetSubscriptionNumber("SUB-" + quoteId).observedRevision(0L).build());
        amendment.getTerms().setStartDate("2028-07-01");
        amendment.getTerms().setEndDate("2029-12-31");
        amendment.getTerms().setTermMonths(18);
        QuoteAcceptedEvent.LineItemSnapshot line = amendment.getLineItems().getFirst();
        line.setLineUuid("11111111-1111-4111-8111-000000000207");
        line.setSourceSubscriptionLineUuid("11111111-1111-4111-8111-000000000201");
        line.setQuantity(70);
        line.setExtendedAmount(new BigDecimal("3150"));
        line.setRamp(List.of(ramp(1, 1, 6, "45", "3150", "18900"),
                ramp(2, 7, 12, "50", "3500", "42000")));
        amendment.setTotalAmount(new BigDecimal("3150"));
        amendment.getPricing().setTotalAmount(new BigDecimal("3150"));
        amendment.getPricing().setTcv(new BigDecimal("60900"));
        amendment.getPricing().setAcv(new BigDecimal("40600"));
        amendment.getPricing().setArr(new BigDecimal("37800"));
        amendment.getPricing().setExitArr(new BigDecimal("42000"));
        subscriptions.apply(amendment);
        subscriptions.apply(amendment);

        assertThat(jdbc.queryForObject("select commercial_revision from subscriptions where id = ?",
                Long.class, subscriptionId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from applied_quote_acceptances where accepted_quote_id = ?",
                Long.class, amendmentId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from assets where subscription_id = ?",
                Long.class, subscriptionId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where topic in (?, ?)",
                Long.class, KafkaTopics.ASSET_CREATED, KafkaTopics.ASSET_AUDIT)).isZero();
    }

    private static QuoteAcceptedEvent.RampPeriodSnapshot ramp(int sequence, int startMonth, int months,
                                                              String unit, String monthly, String period) {
        return QuoteAcceptedEvent.RampPeriodSnapshot.builder()
                .sequence(sequence).startMonth(startMonth).months(months).quantity(70)
                .netUnitPrice(new BigDecimal(unit)).netMonthly(new BigDecimal(monthly))
                .periodAmount(new BigDecimal(period)).build();
    }
}
