package com.opencrm.contract.documents.apply;

import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.repository.documents.AmendmentApplicationUnitRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC-S4-1 against PostgreSQL. Tagged integration so the Docker-free unit stage does not start a container.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false",
        "opencrm.security.mode=dev"})
class DocumentApplyIntegrationTest {

    private static final String HASH = "ab".repeat(32);

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
    private AmendmentApplicationUnitRepository units;

    @Autowired
    private DocumentApplyGateway gateway;

    @Test
    void twoUnitsApplied() {
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        seed(tenant, documentId, first, second, false);

        ApplyResult result = orchestrator.apply(new ApplyCommand(documentId, tenant, "quote-service",
                "ROLE_INTERNAL_INVOKE", "corr-int", Map.of()));

        assertThat(result.replayed()).isFalse();
        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(jdbc.queryForObject("select status from amendment_documents where id = ?", String.class, documentId))
                .isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("select count(*) from document_apply_ledger where document_id = ?", Long.class, documentId))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "select count(*) from subscription_rate_periods where amendment_document_line_id in "
                        + "(select id from amendment_document_lines where document_id = ?)", Long.class, documentId))
                .isEqualTo(2L);
        Long appliedEvents = jdbc.queryForObject(
                "select count(*) from amendment_document_events where document_id = ? and event_type = ?",
                Long.class, documentId, DocumentApplyOrchestrator.EVENT_UNIT_APPLIED);
        assertThat(appliedEvents).isEqualTo(2L);

        ApplyResult replay = orchestrator.apply(new ApplyCommand(documentId, tenant, "quote-service",
                "ROLE_INTERNAL_INVOKE", "corr-int", Map.of()));
        assertThat(replay.replayed()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from document_apply_ledger where document_id = ?", Long.class, documentId))
                .isEqualTo(2L);
    }

    @Test
    void partialReplayPersistsAttemptDigestAndRejectsChangedAppliedUnit() {
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        seed(tenant, documentId, UUID.randomUUID(), UUID.randomUUID(), false);
        jdbc.update("""
                insert into document_apply_ledger (tenant_id, document_id, unit_no, apply_key_digest, created_by)
                values (?, ?, 2, ?, 'earlier')
                """, tenant, documentId, "cd".repeat(32));
        ApplyCommand command = new ApplyCommand(documentId, tenant, "quote-service", "ROLE_INTERNAL_INVOKE",
                "corr-int", Map.of());

        assertThat(orchestrator.apply(command).status()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(jdbc.queryForObject("""
                select apply_key_digest from amendment_application_units where document_id = ? and unit_no = 2
                """, String.class, documentId)).matches("[0-9a-f]{64}");
        assertThat(orchestrator.apply(command).replayed()).isTrue();

        jdbc.update("update amendment_application_units set observed_commercial_revision = 99 where document_id = ? and unit_no = 1",
                documentId);
        assertThatThrownBy(() -> orchestrator.apply(command))
                .isInstanceOfSatisfying(DocumentApplyException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT"));
        assertThat(jdbc.queryForObject("""
                select count(*) from amendment_document_events where document_id = ? and event_type = 'APPLY_REFUSED'
                """, Long.class, documentId)).isEqualTo(1L);
    }

    @Test
    void interruptedApplyRejectsChangedCommittedUnitBeforePendingSibling() {
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        seed(tenant, documentId, UUID.randomUUID(), UUID.randomUUID(), false);
        var first = units.findByDocumentIdOrderByDependencyOrderAscUnitNoAsc(documentId).getFirst();
        String digest = DocumentApplyDigest.unitDigest(documentId, first,
                gateway.lines(documentId, 1).stream().filter(line -> line.unitNo() == 1).toList());
        jdbc.update("""
                update amendment_application_units set status = 'APPLIED', apply_key_digest = ?,
                    observed_commercial_revision = 99, dependency_order = 2
                where document_id = ? and unit_no = 1
                """, digest, documentId);
        jdbc.update("update amendment_application_units set dependency_order = 1 where document_id = ? and unit_no = 2",
                documentId);
        jdbc.update("""
                insert into document_apply_ledger (tenant_id, document_id, unit_no, apply_key_digest, created_by)
                values (?, ?, 1, ?, 'earlier')
                """, tenant, documentId, digest);
        ApplyCommand command = new ApplyCommand(documentId, tenant, "quote-service", "ROLE_INTERNAL_INVOKE",
                "corr-int", Map.of());

        assertThatThrownBy(() -> orchestrator.apply(command))
                .isInstanceOfSatisfying(DocumentApplyException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT"));
        assertThat(jdbc.queryForObject("""
                select status from amendment_application_units where document_id = ? and unit_no = 2
                """, String.class, documentId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("""
                select count(*) from document_apply_ledger where document_id = ?
                """, Long.class, documentId)).isEqualTo(1L);
    }

    private void seed(UUID tenant, UUID documentId, UUID firstSubscription, UUID secondSubscription, boolean conflict) {
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
            insertSubscription(firstSubscription);
            insertSubscription(secondSubscription);
            insertUnit(documentId, tenant, 1, firstSubscription);
            insertUnit(documentId, tenant, 2, secondSubscription);
            insertLine(documentId, tenant, 1, firstSubscription);
            insertLine(documentId, tenant, 2, secondSubscription);
            if (conflict) {
                jdbc.update("""
                        insert into document_apply_ledger (tenant_id, document_id, unit_no, apply_key_digest, created_by)
                        values (?, ?, 1, ?, 'earlier')
                        """, tenant, documentId, "cd".repeat(32));
            }
        });
    }

    private void insertSubscription(UUID uuid) {
        jdbc.update("""
                insert into subscriptions (uuid, subscription_number, customer_id, quote_id, status, plan_name,
                    start_date, end_date, commercial_revision, created_by)
                values (?, ?, ?, ?, 'ACTIVE', 'Plan', '2026-01-01', '2026-12-31', 0, 'test')
                """, uuid.toString(), "SUB-" + uuid, uuid.toString(), "Q-" + uuid);
    }

    private void insertUnit(UUID documentId, UUID tenant, int unitNo, UUID subscription) {
        jdbc.update("""
                insert into amendment_application_units (
                    document_id, unit_no, tenant_id, dependency_order, target_type, target_ref,
                    observed_commercial_revision, status, created_by)
                values (?, ?, ?, ?, 'SUBSCRIPTION', ?, 0, 'PENDING', 'test')
                """, documentId, unitNo, tenant, unitNo, subscription.toString());
    }

    private void insertLine(UUID documentId, UUID tenant, int unitNo, UUID subscription) {
        jdbc.update("""
                insert into amendment_document_lines (
                    id, tenant_id, document_id, revision_no, line_no, change_line_ref, action_code, business_category,
                    apply_target_type, target_subscription_id, quantity_semantics, start_qty, delta_qty, end_qty,
                    effective_from, effective_to, charge_type, price_as_of, product_external_id, pricebook_version,
                    configuration_hash, policy_slot, application_unit_no, created_by)
                values (?, ?, ?, 1, ?, ?, 'ADD', 'UPSELL', 'SUBSCRIPTION', ?, 'ABSOLUTE', 0, 1, 1,
                    '2026-06-01', '2026-12-31', 'RECURRING', 'EFFECTIVE_DATE', 'prod', 'pb-1', ?, 'AMENDMENT', ?, 'test')
                """, UUID.randomUUID(), tenant, documentId, unitNo, UUID.randomUUID(), subscription, HASH, unitNo);
    }

    @Test
    void digestConflictIsAuditedOnTheDocumentChain() {
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        seed(tenant, documentId, UUID.randomUUID(), UUID.randomUUID(), true);

        assertThatThrownBy(() -> orchestrator.apply(new ApplyCommand(documentId, tenant, "quote-service",
                "ROLE_INTERNAL_INVOKE", "corr-int", Map.of())))
                .isInstanceOf(DocumentApplyException.class);
        assertThat(jdbc.queryForObject("select status from amendment_documents where id = ?", String.class, documentId))
                .isEqualTo("EXECUTED");
        Long audits = jdbc.queryForObject(
                "select count(*) from amendment_document_events where document_id = ? and reason_code = 'DIGEST_CONFLICT'",
                Long.class, documentId);
        assertThat(audits).isEqualTo(1L);
        Long outbox = jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?", Long.class,
                documentId.toString());
        assertThat(outbox).isEqualTo(1L);
    }
}
