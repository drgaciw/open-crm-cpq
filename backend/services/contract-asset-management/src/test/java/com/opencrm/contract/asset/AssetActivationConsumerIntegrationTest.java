package com.opencrm.contract.asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.EventHeaders;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.event.QuoteAcceptedEventConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard for {@code AcceptedQuoteSubscriptionService#setAssetActivation}. The consumer bean is the
 * real Spring bean. Assets appear only when that optional setter was called.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false",
        "opencrm.assets.legacy-acceptance-write-enabled=true"})
class AssetActivationConsumerIntegrationTest {

    private static final String NEW_LINE_A = "11111111-1111-4111-8111-000000000201";
    private static final String NEW_LINE_B = "11111111-1111-4111-8111-000000000202";
    private static final String AMENDED_LINE = "11111111-1111-4111-8111-000000000207";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(384L * 1024 * 1024));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String url = POSTGRES.getJdbcUrl();
            return url + (url.contains("?") ? "&" : "?") + "currentSchema=contract";
        });
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired QuoteAcceptedEventConsumer consumer;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    private final AtomicLong offsets = new AtomicLong(1);

    @Test
    void consumerCreatesOneAssetPerNewLineAndOnePerAmendedLine() throws Exception {
        QuoteAcceptedEvent sale = AssetActivationTestSupport.twoLines(AssetActivationTestSupport.fixture());
        String saleQuoteId = "it-consumer-" + UUID.randomUUID();
        sale.setQuoteId(saleQuoteId);
        sale.getQuote().setQuoteId(saleQuoteId);

        consumer.handleQuoteAccepted(record(sale));

        Long subscriptionId = jdbc.queryForObject(
                "select id from subscriptions where subscription_number = ?",
                Long.class, "SUB-" + saleQuoteId);
        assertThat(linesOf(subscriptionId)).containsExactlyInAnyOrder(NEW_LINE_A, NEW_LINE_B);
        assertThat(accountIds(subscriptionId)).containsOnly(sale.getCustomerId());
        assertThat(outboxCount(KafkaTopics.ASSET_CREATED)).isEqualTo(2L);
        assertThat(outboxCount(KafkaTopics.ASSET_AUDIT)).isEqualTo(2L);
        assertThat(EventContractRegistry.ASSET_AUDIT_RECORDED_V1.eventType()).isEqualTo("AssetAuditRecorded");
        assertThat(EventContractRegistry.ASSET_AUDIT_RECORDED_V1.topic()).isEqualTo(KafkaTopics.ASSET_AUDIT);

        new TransactionTemplate(transactions).executeWithoutResult(status ->
                jdbc.update("update subscriptions set status = 'ACTIVE' where id = ?", subscriptionId));

        QuoteAcceptedEvent amendment = amendment(sale.getCustomerId(), "SUB-" + saleQuoteId);
        consumer.handleQuoteAccepted(record(amendment));

        assertThat(linesOf(subscriptionId)).containsExactlyInAnyOrder(NEW_LINE_A, NEW_LINE_B, AMENDED_LINE);
        assertThat(accountIds(subscriptionId)).containsOnly(sale.getCustomerId());
        assertThat(outboxCount(KafkaTopics.ASSET_CREATED)).isEqualTo(3L);
        assertThat(outboxCount(KafkaTopics.ASSET_AUDIT)).isEqualTo(3L);
    }

    private QuoteAcceptedEvent amendment(String customerId, String subscriptionNumber) throws Exception {
        QuoteAcceptedEvent event = AssetActivationTestSupport.fixture();
        String quoteId = "it-amend-" + UUID.randomUUID();
        event.setQuoteId(quoteId);
        event.getQuote().setQuoteId(quoteId);
        event.setCustomerId(customerId);
        event.getCustomer().setCustomerId(customerId);
        event.getDeal().setDealType("AMENDMENT");
        event.setSubscriptionChange(QuoteAcceptedEvent.SubscriptionChangeSnapshot.builder()
                .targetSubscriptionNumber(subscriptionNumber)
                .observedRevision(0L)
                .build());
        event.getTerms().setStartDate("2028-07-01");
        event.getTerms().setEndDate("2029-12-31");
        event.getTerms().setTermMonths(18);
        QuoteAcceptedEvent.LineItemSnapshot line = event.getLineItems().getFirst();
        line.setLineUuid(AMENDED_LINE);
        line.setSourceSubscriptionLineUuid(NEW_LINE_A);
        line.setQuantity(70);
        line.setExtendedAmount(new BigDecimal("3150"));
        line.setRamp(List.of(
                ramp(1, 1, 6, 70, "45", "3150", "18900"),
                ramp(2, 7, 12, 70, "50", "3500", "42000")));
        event.setTotalAmount(new BigDecimal("3150"));
        event.getPricing().setTotalAmount(new BigDecimal("3150"));
        event.getPricing().setTcv(new BigDecimal("60900"));
        event.getPricing().setAcv(new BigDecimal("40600"));
        event.getPricing().setArr(new BigDecimal("37800"));
        event.getPricing().setExitArr(new BigDecimal("42000"));
        return event;
    }

    private static QuoteAcceptedEvent.RampPeriodSnapshot ramp(int sequence, int startMonth, int months,
                                                              int quantity, String unit, String monthly, String period) {
        return QuoteAcceptedEvent.RampPeriodSnapshot.builder()
                .sequence(sequence).startMonth(startMonth).months(months).quantity(quantity)
                .netUnitPrice(new BigDecimal(unit)).netMonthly(new BigDecimal(monthly))
                .periodAmount(new BigDecimal(period)).build();
    }

    private ConsumerRecord<String, String> record(QuoteAcceptedEvent event) throws Exception {
        ConsumerRecord<String, String> message = new ConsumerRecord<>(
                KafkaTopics.QUOTE_STATUS_ACCEPTED, 0, offsets.getAndIncrement(), event.getQuoteId(),
                objectMapper.writeValueAsString(event));
        message.headers().add(EventHeaders.EVENT_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        return message;
    }

    private List<String> linesOf(Long subscriptionId) {
        return jdbc.queryForList(
                "select contract_line_id from assets where subscription_id = ? and is_deleted = false",
                String.class, subscriptionId);
    }

    private List<String> accountIds(Long subscriptionId) {
        return jdbc.queryForList(
                "select account_id from assets where subscription_id = ? and is_deleted = false",
                String.class, subscriptionId);
    }

    private long outboxCount(String topic) {
        Long count = jdbc.queryForObject(
                "select count(*) from outbox_events where topic = ?", Long.class, topic);
        return count == null ? 0L : count;
    }
}
