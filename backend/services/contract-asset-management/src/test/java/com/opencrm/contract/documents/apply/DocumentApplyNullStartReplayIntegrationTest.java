package com.opencrm.contract.documents.apply;

import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-715 / AC-651-4 against PostgreSQL.
 * <p>
 * {@code amendment_document_lines.effective_from} and {@code subscription_rate_periods.start_date}
 * are NOT NULL, so this IT never seeds a null date. A test-only gateway view presents a missing
 * change-line start to the apply path; the durable block and idempotent replay are asserted in the
 * real document, unit, ledger, and event tables.
 */
@Tag("integration")
@Testcontainers
@Import(DocumentApplyNullStartReplayIntegrationTest.NullStartViewConfiguration.class)
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false",
        "opencrm.security.mode=dev"})
class DocumentApplyNullStartReplayIntegrationTest {

    private static final String HASH = "ab".repeat(32);
    private static final String START_DATE_MISSING = "START_DATE_MISSING";
    private static final String BLOCK_DETAIL =
            "apply:START_DATE_MISSING:the change line or source period has no start date";

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

    @Autowired
    private DocumentApplyOrchestrator orchestrator;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private AssetLifecycleReadiness testReadiness;

    @BeforeEach
    void ready() {
        Mockito.when(testReadiness.isReady()).thenReturn(true);
        NullStartViewConfiguration.PRESENT_NULL_EFFECTIVE_FROM.set(true);
    }

    @AfterEach
    void clearView() {
        NullStartViewConfiguration.PRESENT_NULL_EFFECTIVE_FROM.set(false);
    }

    @Test
    void nullEffectiveFromBlocksDurablyAndReplayIsIdempotent() {
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID subscription = UUID.randomUUID();
        seedAdd(tenant, documentId, subscription);

        assertThat(jdbc.queryForObject(
                "select effective_from is not null from amendment_document_lines where document_id = ?",
                Boolean.class, documentId)).isTrue();

        ApplyCommand command = new ApplyCommand(documentId, tenant, "quote-service",
                "ROLE_INTERNAL_INVOKE", "corr-null-start", Map.of());
        ApplyResult first = orchestrator.apply(command);

        assertThat(first.replayed()).isFalse();
        assertThat(first.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(first.unitOutcomes()).containsExactly(
                new ApplyResult.UnitOutcome(1, "BLOCKED", START_DATE_MISSING));
        assertDurableBlock(documentId, subscription);
        long eventsAfterFirst = eventCount(documentId);
        long blockedUnits = jdbc.queryForObject(
                "select count(*) from amendment_document_events where document_id = ? and event_type = ?",
                Long.class, documentId, DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED);
        long blockedDocuments = jdbc.queryForObject(
                "select count(*) from amendment_document_events where document_id = ? and event_type = ?",
                Long.class, documentId, DocumentApplyOrchestrator.EVENT_DOCUMENT_BLOCKED);
        assertThat(blockedUnits).isEqualTo(1L);
        assertThat(blockedDocuments).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                select payload_ref from amendment_document_events
                where document_id = ? and event_type = ? and unit_no = 1
                """, String.class, documentId, DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED))
                .isEqualTo(BLOCK_DETAIL);

        ApplyResult second = orchestrator.apply(command);

        assertThat(second.replayed()).isTrue();
        assertThat(second.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(second.unitOutcomes()).isEqualTo(first.unitOutcomes());
        assertDurableBlock(documentId, subscription);
        assertThat(eventCount(documentId)).isEqualTo(eventsAfterFirst);
        assertThat(jdbc.queryForObject(
                "select count(*) from amendment_document_events where document_id = ? and event_type = ?",
                Long.class, documentId, DocumentApplyOrchestrator.EVENT_UNIT_APPLIED)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id = ?",
                Long.class, documentId.toString())).isEqualTo(eventsAfterFirst);
    }

    @Test
    void datedLineStillAppliesWhenNullStartViewIsOff() {
        NullStartViewConfiguration.PRESENT_NULL_EFFECTIVE_FROM.set(false);
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID subscription = UUID.randomUUID();
        seedAdd(tenant, documentId, subscription);

        ApplyResult result = orchestrator.apply(new ApplyCommand(documentId, tenant, "quote-service",
                "ROLE_INTERNAL_INVOKE", "corr-dated", Map.of()));

        assertThat(result.replayed()).isFalse();
        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(jdbc.queryForObject("select count(*) from document_apply_ledger where document_id = ?",
                Long.class, documentId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select count(*) from subscription_rate_periods where amendment_document_line_id in "
                        + "(select id from amendment_document_lines where document_id = ?)",
                Long.class, documentId)).isEqualTo(1L);
    }

    private void assertDurableBlock(UUID documentId, UUID subscription) {
        assertThat(jdbc.queryForObject("select status from amendment_documents where id = ?",
                String.class, documentId)).isEqualTo("APPLY_BLOCKED");
        assertThat(jdbc.queryForObject(
                "select status from amendment_application_units where document_id = ? and unit_no = 1",
                String.class, documentId)).isEqualTo("BLOCKED");
        assertThat(jdbc.queryForObject(
                "select block_reason from amendment_application_units where document_id = ? and unit_no = 1",
                String.class, documentId)).isEqualTo(START_DATE_MISSING);
        assertThat(jdbc.queryForObject("select count(*) from document_apply_ledger where document_id = ?",
                Long.class, documentId)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from subscription_rate_periods where amendment_document_line_id in "
                        + "(select id from amendment_document_lines where document_id = ?)",
                Long.class, documentId)).isZero();
        assertThat(jdbc.queryForObject("select commercial_revision from subscriptions where uuid = ?",
                Long.class, subscription.toString())).isZero();
        assertThat(jdbc.queryForObject(
                "select effective_from is not null from amendment_document_lines where document_id = ?",
                Boolean.class, documentId)).isTrue();
    }

    private long eventCount(UUID documentId) {
        return jdbc.queryForObject("select count(*) from amendment_document_events where document_id = ?",
                Long.class, documentId);
    }

    private void seedAdd(UUID tenant, UUID documentId, UUID subscriptionUuid) {
        UUID contractId = UUID.randomUUID();
        UUID lifecycleId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("""
                    insert into contracts (id, tenant_id, contract_number, customer_account_id, term_model, start_date, end_date,
                        status, last_executed_seq, created_by)
                    values (?, ?, ?, ?, 'TERMED', '2026-01-01', '2026-12-31', 'ACTIVE', 1, 'test')
                    """, contractId, tenant, "C-" + contractId, customer);
            jdbc.update("""
                    insert into amendment_documents (
                        id, tenant_id, contract_id, lifecycle_change_id, lifecycle_change_revision, intent, initiation_trigger,
                        provisional_number, amendment_seq, amendment_number, document_kind, status, current_revision_no,
                        executed_revision_no, signature_policy, action_date, contract_effective_date, co_term_date, currency_code,
                        tcv_delta, arr_delta, one_time_total, credit_total, content_digest, evidence_class, created_by)
                    values (?, ?, ?, ?, 1, 'AMEND', 'MANUAL', ?, 1, ?, 'AMENDMENT', 'EXECUTED', 1, 1, 'NOT_REQUIRED',
                        '2026-06-01', '2026-06-01', '2026-12-31', 'USD', 0, 0, 0, 0, ?, 'STANDARD', 'test')
                    """, documentId, tenant, contractId, lifecycleId, "P-" + documentId, "C-A001", HASH);
            jdbc.update("""
                    insert into amendment_document_revisions (
                        document_id, revision_no, tenant_id, lifecycle_change_revision, target_quote_id, content_digest,
                        inputs_snapshot_ref, status_at_close, created_by)
                    values (?, 1, ?, 1, ?, ?, 'snapshot', 'EXECUTED', 'test')
                    """, documentId, tenant, quoteId, HASH);
            jdbc.update("""
                    insert into subscriptions (uuid, subscription_number, customer_id, quote_id, status, plan_name,
                        start_date, end_date, commercial_revision, created_by)
                    values (?, ?, ?, ?, 'ACTIVE', 'Plan', '2026-01-01', '2026-12-31', 0, 'test')
                    """, subscriptionUuid.toString(), "SUB-" + subscriptionUuid, subscriptionUuid.toString(),
                    "Q-" + subscriptionUuid);
            jdbc.update("update subscriptions set contract_id = ?, tenant_id = ? where uuid = ?",
                    contractId, tenant, subscriptionUuid.toString());
            jdbc.update("""
                    insert into amendment_application_units (
                        document_id, unit_no, tenant_id, dependency_order, target_type, target_ref,
                        observed_commercial_revision, status, created_by)
                    values (?, 1, ?, 1, 'SUBSCRIPTION', ?, 0, 'PENDING', 'test')
                    """, documentId, tenant, subscriptionUuid.toString());
            // NOT NULL column stays populated; the gateway view presents null to apply.
            jdbc.update("""
                    insert into amendment_document_lines (
                        id, tenant_id, document_id, revision_no, line_no, change_line_ref, action_code, business_category,
                        apply_target_type, target_subscription_id, quantity_semantics, start_qty, delta_qty, end_qty,
                        effective_from, effective_to, charge_type, price_as_of, product_external_id, pricebook_version,
                        configuration_hash, policy_slot, application_unit_no, created_by)
                    values (?, ?, ?, 1, 1, ?, 'ADD', 'UPSELL', 'SUBSCRIPTION', ?, 'ABSOLUTE', 0, 1, 1,
                        '2026-06-01', '2026-12-31', 'RECURRING', 'EFFECTIVE_DATE', 'prod', 'pb-1', ?, 'AMENDMENT', 1, 'test')
                    """, UUID.randomUUID(), tenant, documentId, UUID.randomUUID(), subscriptionUuid, HASH);
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class NullStartViewConfiguration {

        /** When true, {@link DocumentApplyGateway#lines} returns null {@code effectiveFrom}. */
        static final AtomicBoolean PRESENT_NULL_EFFECTIVE_FROM = new AtomicBoolean(false);

        @Bean
        @Primary
        AssetLifecycleReadiness testAssetLifecycleReadiness() {
            return Mockito.mock(AssetLifecycleReadiness.class);
        }

        @Bean
        @Primary
        DocumentApplyGateway nullStartApplyGateway(JdbcDocumentApplyGateway jdbc) {
            return new DocumentApplyGateway() {
                @Override
                public void lockDocument(UUID documentId) {
                    jdbc.lockDocument(documentId);
                }

                @Override
                public void lockSubscription(String subscriptionUuid) {
                    jdbc.lockSubscription(subscriptionUuid);
                }

                @Override
                public Optional<ApplyTarget> subscription(String subscriptionUuid) {
                    return jdbc.subscription(subscriptionUuid);
                }

                @Override
                public TargetContractMembership targetContractMembership(long subscriptionId, UUID contractId, UUID tenantId) {
                    return jdbc.targetContractMembership(subscriptionId, contractId, tenantId);
                }

                @Override
                public String contractTenantBlock(long subscriptionId) {
                    return jdbc.contractTenantBlock(subscriptionId);
                }

                @Override
                public List<ApplyLine> lines(UUID documentId, int revisionNo) {
                    List<ApplyLine> lines = jdbc.lines(documentId, revisionNo);
                    if (!PRESENT_NULL_EFFECTIVE_FROM.get()) {
                        return lines;
                    }
                    return lines.stream()
                            .map(line -> new ApplyLine(
                                    line.id(),
                                    line.lineNo(),
                                    line.unitNo(),
                                    line.action(),
                                    line.sourceAcceptedLineUuid(),
                                    line.endQty(),
                                    null,
                                    line.effectiveTo(),
                                    line.pricebookVersion(),
                                    line.productExternalId(),
                                    line.unitPrice()))
                            .toList();
                }

                @Override
                public void bindAmendmentLine(long periodId, UUID lineId, int revisionNo) {
                    jdbc.bindAmendmentLine(periodId, lineId, revisionNo);
                }
            };
        }
    }
}
