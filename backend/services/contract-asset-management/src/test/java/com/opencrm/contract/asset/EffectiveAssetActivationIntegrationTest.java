package com.opencrm.contract.asset;

import com.opencrm.common.event.KafkaTopics;
import com.opencrm.contract.ContractAssetManagementApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false",
        "opencrm.assets.effective-activation.enabled=false"})
class EffectiveAssetActivationIntegrationTest {
    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2030, 2, 1);
    private static final LocalDate TERM_END = LocalDate.of(2030, 12, 31);

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

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EffectiveAssetActivation activation;
    @Autowired AssetHistoryProjection history;

    @BeforeEach
    void authorizeRead() {
        AssetReadTestCaller.signIn();
    }

    @AfterEach
    void clearReadAuthorization() {
        AssetReadTestCaller.clear();
    }

    @Test
    void futureActivationCommitsExactlyOneAssetHistoryFactAndOutboxPair() {
        Fixture fixture = fixture();
        UUID activation = UUID.randomUUID();

        stage(fixture, activation);
        stage(fixture, activation);
        assertThat(receiptStatus(fixture)).isEqualTo("PENDING");
        assertThat(project(EFFECTIVE_DATE.minusDays(1))).isFalse();
        assertThat(assetCount(fixture)).isZero();

        assertThat(project(EFFECTIVE_DATE)).isTrue();
        assertThat(project(EFFECTIVE_DATE)).isFalse();
        stage(fixture, activation);
        assertThat(receiptStatus(fixture)).isEqualTo("APPLIED");
        assertThat(assetCount(fixture)).isEqualTo(1);
        assertThat(history.installedBase(fixture.externalCustomer(), EFFECTIVE_DATE.minusDays(1))).isEmpty();
        assertThat(history.installedBase(fixture.externalCustomer(), EFFECTIVE_DATE))
                .singleElement().satisfies(asset -> {
                    assertThat(asset.getContractLineId()).isEqualTo(fixture.lineId().toString());
                    assertThat(asset.getProductRef()).isEqualTo("product-authoritative");
                    assertThat(asset.getQuantity()).isEqualByComparingTo("3");
                    assertThat(asset.getEffectiveStart()).isEqualTo(EFFECTIVE_DATE);
                    assertThat(asset.getEffectiveEnd()).isEqualTo(TERM_END);
                    assertThat(publications(asset.getAssetId())).isEqualTo(2);
                    assertThat(history.history(asset.getAssetId())).hasSize(1);
                });
        assertThat(jdbc.queryForObject("""
                select outbox.causation_id from outbox_events outbox join asset_activation_receipts receipt
                  on receipt.created_event_id = outbox.uuid where receipt.contract_line_id = ?
                """, String.class, fixture.lineId())).isEqualTo(activation.toString());
    }

    @Test
    void crossTenantLinkedRecordsCannotStageActivation() {
        Fixture fixture = fixture();
        jdbc.update("update subscriptions set tenant_id = ? where id = ?", UUID.randomUUID(), fixture.subscriptionId());

        assertThatThrownBy(() -> stage(fixture, UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        assertThat(assetCount(fixture)).isZero();
    }

    @Test
    void preexistingLegacyAssetRequiresReconciliation() {
        Fixture fixture = fixture();
        stage(fixture, UUID.randomUUID());
        jdbc.update("""
                insert into assets (uuid, account_id, contract_line_id, subscription_id, status,
                                    effective_start, effective_end)
                values (?, ?, 'legacy-aggregate', ?, 'ACTIVE', ?, ?)
                """, UUID.randomUUID().toString(), fixture.externalCustomer(), fixture.subscriptionId(),
                EFFECTIVE_DATE, TERM_END);

        assertThat(project(EFFECTIVE_DATE)).isTrue();
        assertThat(receiptStatus(fixture)).isEqualTo("NEEDS_RECONCILIATION");
        assertThat(assetCount(fixture)).isEqualTo(1);
        assertThat(history.installedBase(fixture.externalCustomer(), EFFECTIVE_DATE)).isEmpty();
    }

    @Test
    void outboxFailureLeavesReceiptPendingAndProjectionRetryable() {
        Fixture fixture = fixture();
        UUID activationId = UUID.randomUUID();
        stage(fixture, activationId);
        jdbc.execute("""
                alter table outbox_events add constraint test_reject_asset_audit
                check (topic <> 'cpq.asset.audit') not valid
                """);
        try {
            assertThatThrownBy(() -> project(EFFECTIVE_DATE)).hasStackTraceContaining("test_reject_asset_audit");
            assertThat(receiptStatus(fixture)).isEqualTo("PENDING");
            assertThat(assetCount(fixture)).isZero();
            assertThat(history.installedBase(fixture.externalCustomer(), EFFECTIVE_DATE)).isEmpty();
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where causation_id = ?",
                    Long.class, activationId.toString())).isZero();
        } finally {
            jdbc.execute("alter table outbox_events drop constraint test_reject_asset_audit");
        }
        assertThat(project(EFFECTIVE_DATE)).isTrue();
        assertThat(receiptStatus(fixture)).isEqualTo("APPLIED");
    }

    @Test
    void receiptRollsBackWithTheSourceActivationTransaction() {
        Fixture fixture = fixture();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            activation.stage(fixture.subscriptionId(), fixture.lineId(), UUID.randomUUID(), EFFECTIVE_DATE);
            status.setRollbackOnly();
        });

        assertThat(project(EFFECTIVE_DATE)).isFalse();
        assertThat(assetCount(fixture)).isZero();
    }

    @Test
    void activatingTheSameLineWithAnUnrelatedSourceConflicts() {
        Fixture fixture = fixture();
        stage(fixture, UUID.randomUUID());

        assertThatThrownBy(() -> stage(fixture, UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        assertThat(project(EFFECTIVE_DATE)).isTrue();
        assertThat(assetCount(fixture)).isEqualTo(1);
    }

    @Test
    void changedQuoteProvenanceRequiresReconciliation() {
        Fixture fixture = fixture();
        stage(fixture, UUID.randomUUID());
        String changedQuote = "changed-" + UUID.randomUUID();
        jdbc.update("update subscriptions set quote_id = ? where id = ?", changedQuote, fixture.subscriptionId());
        jdbc.update("update contract_lines set quote_id = ? where id = ?", changedQuote, fixture.lineId());

        assertThat(project(EFFECTIVE_DATE)).isTrue();
        assertThat(receiptStatus(fixture)).isEqualTo("NEEDS_RECONCILIATION");
        assertThat(assetCount(fixture)).isZero();
    }

    @Test
    void workersContendingForOneReceiptCreateOneAsset() throws Exception {
        Fixture fixture = fixture();
        stage(fixture, UUID.randomUUID());

        CountDownLatch begin = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                begin.await();
                return project(EFFECTIVE_DATE);
            });
            var second = workers.submit(() -> {
                begin.await();
                return project(EFFECTIVE_DATE);
            });
            begin.countDown();

            assertThat(new Boolean[]{first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(receiptStatus(fixture)).isEqualTo("APPLIED");
        assertThat(assetCount(fixture)).isEqualTo(1);
    }

    @Test
    void theOldestDueEffectiveDateIsAppliedFirstAfterAnOutage() {
        Fixture later = fixture();
        Fixture earlier = fixture();
        jdbc.update("update contract_lines set effective_from = ? where id = ?",
                EFFECTIVE_DATE.minusDays(1), earlier.lineId());
        stage(later, UUID.randomUUID());
        stage(earlier, UUID.randomUUID());

        assertThat(project(EFFECTIVE_DATE)).isTrue();
        assertThat(receiptStatus(earlier)).isEqualTo("APPLIED");
        assertThat(receiptStatus(later)).isEqualTo("PENDING");
        assertThat(project(EFFECTIVE_DATE)).isTrue();
        assertThat(receiptStatus(later)).isEqualTo("APPLIED");
    }

    private void stage(Fixture fixture, UUID activationId) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> activation.stage(
                fixture.subscriptionId(), fixture.lineId(), activationId, EFFECTIVE_DATE.minusDays(2)));
    }

    private boolean project(LocalDate date) {
        return activation.applyNext(date);
    }

    private Fixture fixture() {
        UUID tenant = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        UUID contract = UUID.randomUUID();
        UUID line = UUID.randomUUID();
        String customer = "customer-" + account;
        String quote = "quote-" + line;
        jdbc.update("""
                insert into contracts (id, tenant_id, contract_number, customer_account_id, term_model, start_date,
                                       end_date, status, created_by)
                values (?, ?, ?, ?, 'TERMED', ?, ?, 'ACTIVE', 'test')
                """, contract, tenant, contract.toString(), account, EFFECTIVE_DATE.minusMonths(1), TERM_END);
        jdbc.update("""
                insert into contract_lines (id, tenant_id, contract_id, line_no, accepted_line_uuid, quote_id,
                                            product_external_id, quantity, status, effective_from, effective_to,
                                            charge_type, created_by)
                values (?, ?, ?, 1, ?, ?, 'product-authoritative', 3, 'ACTIVE', ?, ?, 'RECURRING', 'test')
                """, line, tenant, contract, UUID.randomUUID().toString(), quote, EFFECTIVE_DATE, TERM_END);
        Long subscription = jdbc.queryForObject("""
                insert into subscriptions (uuid, subscription_number, customer_id, quote_id, status, plan_name,
                                           start_date, end_date, tenant_id, customer_account_id, contract_id, contract_line_id)
                values (?, ?, ?, ?, 'ACTIVE', 'Plan', ?, ?, ?, ?, ?, ?) returning id
                """, Long.class, UUID.randomUUID().toString(), "SUB-" + line, customer, quote,
                EFFECTIVE_DATE.minusMonths(1), TERM_END, tenant, account, contract, line);
        return new Fixture(subscription, line, customer);
    }

    private String receiptStatus(Fixture fixture) {
        return jdbc.queryForObject("select status from asset_activation_receipts where contract_line_id = ?",
                String.class, fixture.lineId());
    }

    private long assetCount(Fixture fixture) {
        return jdbc.queryForObject("select count(*) from assets where subscription_id = ?",
                Long.class, fixture.subscriptionId());
    }

    private long publications(String assetId) {
        return jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ? and topic in (?, ?)",
                Long.class, assetId, KafkaTopics.ASSET_CREATED, KafkaTopics.ASSET_AUDIT);
    }

    private record Fixture(long subscriptionId, UUID lineId, String externalCustomer) {
    }
}
