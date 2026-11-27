package com.opencrm.contract.documents.apply;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.EventContractRegistry.EventContract;
import com.opencrm.common.event.dto.AssetAmendedEvent;
import com.opencrm.common.event.dto.AssetCreatedEvent;
import com.opencrm.common.event.dto.AssetSupersededEvent;
import com.opencrm.common.event.dto.AssetTerminatedEvent;
import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.asset.Asset;
import com.opencrm.contract.asset.AssetChangeApply;
import com.opencrm.contract.asset.AssetRepository;
import com.opencrm.contract.asset.AssetStatus;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import com.opencrm.contract.repository.documents.AmendmentApplicationUnitRepository;
import com.opencrm.contract.repository.documents.AmendmentDocumentRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-927 scoped integration tests (S1 and S2 only; D1 partial-apply retry with asset mutation and
 * D2 fresh apply through the orchestrator remain deferred pending replay wiring and DRG-627 policy):
 * <ul>
 *   <li><b>S1 — Contract characterisation of the held writer (NOT S4 readiness evidence):</b> invokes
 *       {@link AssetChangeApply} directly inside a test transaction on real PostgreSQL (without
 *       {@link DocumentApplyOrchestrator}) and verifies the persisted {@code outbox_events} rows'
 *       topic, event type, version, and deserialized payload for {@code ASSET_AMENDED_V1},
 *       {@code ASSET_TERMINATED_V1}, and {@code ASSET_SUPERSEDED_V1} against
 *       {@link EventContractRegistry}.</li>
 *   <li><b>S2 — Completed-identical replay under the active hold:</b> runs against real PostgreSQL with
 *       the unmocked production {@link AssetLifecycleReadiness} ({@code isReady() == false}), seeds an
 *       already-applied document and matching {@code document_apply_ledger}, replays the identical
 *       request through {@link DocumentApplyOrchestrator}, and asserts the stored result is returned
 *       with zero new asset or asset-outbox rows.</li>
 * </ul>
 * <p>Hold refusal for pending asset mutations is already verified against real PostgreSQL by
 * {@link DocumentApplyIntegrationTest} ({@code defaultHoldAuditsAndLeavesDocumentAndUnitsRetryable},
 * {@code defaultHoldReturns503ThroughTheHttpBoundary}, {@code failedHoldAuditLeavesNoPartialWrites},
 * {@code failedHoldOutboxRollsBackTheAuditAndLeavesUnitsRetryable}) and is not rewritten here.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false"})
class AssetChangeOutboxAndReplayIntegrationTest {

    private static final String HASH = "ab".repeat(32);
    private static final LocalDate TERM_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate TERM_END = LocalDate.of(2026, 12, 31);
    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2026, 6, 1);

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

    @Autowired private AssetChangeApply assetChangeApply;
    @Autowired private DocumentApplyOrchestrator orchestrator;
    @Autowired private AssetLifecycleReadiness readiness;
    @Autowired private AssetRepository assets;
    @Autowired private SubscriptionRepository subscriptions;
    @Autowired private AmendmentDocumentRepository documents;
    @Autowired private AmendmentApplicationUnitRepository units;
    @Autowired private DocumentApplyGateway gateway;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private ObjectMapper objectMapper;

    /**
     * S1 — Contract characterisation of the held writer ({@link AssetChangeApply}), not S4 readiness evidence.
     * Invokes {@link AssetChangeApply#onUnitApplied} directly in a test transaction without the orchestrator
     * and asserts persisted {@code outbox_events} rows for {@code ASSET_AMENDED_V1}, {@code ASSET_TERMINATED_V1},
     * and {@code ASSET_SUPERSEDED_V1} against {@link EventContractRegistry}.
     */
    @Test
    void s1_heldWriterOutboxRowsConformToRegisteredAssetContracts() throws Exception {
        assertThat(readiness.isReady()).isFalse();

        UUID tenant = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        String accountId = "acct-" + UUID.randomUUID();

        Subscription subscription = seedSubscription(tenant, customer, accountId);
        Asset toAmend = seedAsset(subscription, accountId, "line-amend-" + UUID.randomUUID(), "prod-base", new BigDecimal("5.0000"));
        Asset toTerminate = seedAsset(subscription, accountId, "line-term-" + UUID.randomUUID(), "prod-term", new BigDecimal("3.0000"));
        Asset toSupersede = seedAsset(subscription, accountId, "line-swap-" + UUID.randomUUID(), "prod-old", new BigDecimal("2.0000"));

        UUID swapLineId = UUID.randomUUID();
        String openedSuccessorLine = "line-swap-succ-" + UUID.randomUUID();

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assetChangeApply.onUnitApplied(
                    subscription,
                    List.of(new ApplyLine(UUID.randomUUID(), 1, 1, ActionCode.UPDATE,
                            toAmend.getContractLineId(), new BigDecimal("8.0000"),
                            EFFECTIVE_DATE, TERM_END, "pb-1", "prod-amended", null)),
                    documentId, 1, "corr-s1-amend");

            assetChangeApply.onUnitApplied(
                    subscription,
                    List.of(new ApplyLine(UUID.randomUUID(), 2, 2, ActionCode.TERMINATE,
                            toTerminate.getContractLineId(), BigDecimal.ZERO,
                            EFFECTIVE_DATE, null, "pb-1", toTerminate.getProductRef(), null)),
                    documentId, 2, "corr-s1-term");

            assetChangeApply.onUnitApplied(
                    subscription,
                    List.of(new ApplyLine(swapLineId, 3, 3, ActionCode.SWAP,
                            toSupersede.getContractLineId(), new BigDecimal("4.0000"),
                            EFFECTIVE_DATE, TERM_END, "pb-1", "prod-new", null)),
                    documentId, 3, "corr-s1-swap",
                    Map.of(swapLineId, openedSuccessorLine));
        });

        // 1. ASSET_AMENDED_V1
        OutboxRow amendedRow = singleOutboxRow(toAmend.getUuid(), EventContractRegistry.ASSET_AMENDED_V1.topic());
        assertMatchesContract(amendedRow, EventContractRegistry.ASSET_AMENDED_V1, toAmend.getUuid());
        AssetAmendedEvent amended = objectMapper.readValue(amendedRow.payload(), AssetAmendedEvent.class);
        assertThat(amended.assetId()).isEqualTo(toAmend.getUuid());
        assertThat(amended.accountId()).isEqualTo(accountId);
        assertThat(amended.contractLineId()).isEqualTo(toAmend.getContractLineId());
        assertThat(amended.subscriptionId()).isEqualTo(subscription.getId());
        assertThat(amended.productRef()).isEqualTo("prod-amended");
        assertThat(amended.quantity()).isEqualByComparingTo("8.0000");
        assertThat(amended.status()).isEqualTo(AssetStatus.AMENDED.name());
        assertThat(amended.effectiveStart()).isEqualTo(TERM_START);
        assertThat(amended.effectiveEnd()).isEqualTo(TERM_END);
        assertThat(amended.effectiveDate()).isEqualTo(EFFECTIVE_DATE);
        assertThat(amended.sourceAmendmentId()).isEqualTo(documentId.toString());
        assertThat(amended.sourceApplyUnit()).isEqualTo("1");

        // 2. ASSET_TERMINATED_V1
        OutboxRow terminatedRow = singleOutboxRow(toTerminate.getUuid(), EventContractRegistry.ASSET_TERMINATED_V1.topic());
        assertMatchesContract(terminatedRow, EventContractRegistry.ASSET_TERMINATED_V1, toTerminate.getUuid());
        AssetTerminatedEvent terminated = objectMapper.readValue(terminatedRow.payload(), AssetTerminatedEvent.class);
        assertThat(terminated.assetId()).isEqualTo(toTerminate.getUuid());
        assertThat(terminated.accountId()).isEqualTo(accountId);
        assertThat(terminated.contractLineId()).isEqualTo(toTerminate.getContractLineId());
        assertThat(terminated.subscriptionId()).isEqualTo(subscription.getId());
        assertThat(terminated.productRef()).isEqualTo("prod-term");
        assertThat(terminated.status()).isEqualTo(AssetStatus.TERMINATED.name());
        assertThat(terminated.effectiveDate()).isEqualTo(EFFECTIVE_DATE);
        assertThat(terminated.sourceAmendmentId()).isEqualTo(documentId.toString());
        assertThat(terminated.sourceApplyUnit()).isEqualTo("2");

        // 3. ASSET_SUPERSEDED_V1 (and verify successor ASSET_CREATED_V1 persisted first)
        OutboxRow supersededRow = singleOutboxRow(toSupersede.getUuid(), EventContractRegistry.ASSET_SUPERSEDED_V1.topic());
        assertMatchesContract(supersededRow, EventContractRegistry.ASSET_SUPERSEDED_V1, toSupersede.getUuid());
        AssetSupersededEvent superseded = objectMapper.readValue(supersededRow.payload(), AssetSupersededEvent.class);
        assertThat(superseded.assetId()).isEqualTo(toSupersede.getUuid());
        assertThat(superseded.accountId()).isEqualTo(accountId);
        assertThat(superseded.predecessorAssetId()).isEqualTo(toSupersede.getUuid());
        assertThat(superseded.successorAssetId()).isNotBlank();
        assertThat(superseded.status()).isEqualTo(AssetStatus.SUPERSEDED.name());
        assertThat(superseded.effectiveDate()).isEqualTo(EFFECTIVE_DATE);
        assertThat(superseded.sourceAmendmentId()).isEqualTo(documentId.toString());
        assertThat(superseded.sourceApplyUnit()).isEqualTo("3");

        OutboxRow createdSuccessorRow = singleOutboxRow(
                superseded.successorAssetId(), EventContractRegistry.ASSET_CREATED_V1.topic());
        assertMatchesContract(createdSuccessorRow, EventContractRegistry.ASSET_CREATED_V1, superseded.successorAssetId());
        assertThat(createdSuccessorRow.id()).isLessThan(supersededRow.id());
        AssetCreatedEvent created = objectMapper.readValue(createdSuccessorRow.payload(), AssetCreatedEvent.class);
        assertThat(created.assetId()).isEqualTo(superseded.successorAssetId());
        assertThat(created.contractLineId()).isEqualTo(openedSuccessorLine);
        assertThat(created.productRef()).isEqualTo("prod-new");
        assertThat(created.quantity()).isEqualByComparingTo("4.0000");

        // Readiness remains false; no orchestrator or hold bypass occurred.
        assertThat(readiness.isReady()).isFalse();
    }

    /**
     * S2 — Completed-identical replay under the active hold ({@link AssetLifecycleReadiness#isReady()} is
     * real production {@code false}). Replaying an already-applied document with seeded matching
     * {@code document_apply_ledger} rows returns the stored {@code APPLIED} result with {@code replayed=true}
     * and leaves asset rows and asset outbox counts unchanged.
     */
    @Test
    void s2_completedIdenticalReplayUnderHoldReturnsStoredResultWithoutNewAssetOutboxRows() {
        assertThat(readiness.isReady()).isFalse();

        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID firstSubUuid = UUID.randomUUID();
        UUID secondSubUuid = UUID.randomUUID();
        String firstLineUuid = "line-replay-1-" + UUID.randomUUID();
        String secondLineUuid = "line-replay-2-" + UUID.randomUUID();
        ApplyCommand command = new ApplyCommand(documentId, tenant, "quote-service",
                "ROLE_INTERNAL_INVOKE", "corr-s2-replay", Map.of());

        seedCompletedAppliedDocumentWithAssets(tenant, documentId, firstSubUuid, secondSubUuid,
                firstLineUuid, secondLineUuid, command);

        long baselineAssetRows = count("select count(*) from assets");
        long baselineAssetOutbox = count("select count(*) from outbox_events where aggregate_type = 'Asset'");
        long baselineHistoryRows = count("select count(*) from asset_history");
        long baselineLedgerRows = count("select count(*) from document_apply_ledger where document_id = ?", documentId);
        long baselineDocEvents = count("select count(*) from amendment_document_events where document_id = ?", documentId);

        assertThat(baselineAssetOutbox).isGreaterThanOrEqualTo(2L);
        assertThat(baselineLedgerRows).isEqualTo(2L);

        ApplyResult firstReplay = orchestrator.apply(command);
        ApplyResult secondReplay = orchestrator.apply(command);

        assertThat(firstReplay.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(firstReplay.replayed()).isTrue();
        assertThat(firstReplay.unitOutcomes()).isNull();
        assertThat(secondReplay).isEqualTo(firstReplay);

        assertThat(count("select count(*) from assets")).isEqualTo(baselineAssetRows);
        assertThat(count("select count(*) from outbox_events where aggregate_type = 'Asset'"))
                .isEqualTo(baselineAssetOutbox);
        assertThat(count("select count(*) from asset_history")).isEqualTo(baselineHistoryRows);
        assertThat(count("select count(*) from document_apply_ledger where document_id = ?", documentId))
                .isEqualTo(baselineLedgerRows);
        assertThat(count("select count(*) from amendment_document_events where document_id = ?", documentId))
                .isEqualTo(baselineDocEvents);
        assertThat(readiness.isReady()).isFalse();
    }

    private Subscription seedSubscription(UUID tenant, UUID customer, String accountId) {
        UUID subUuid = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> jdbc.update("""
                insert into subscriptions (uuid, subscription_number, customer_id, quote_id, status, plan_name,
                    start_date, end_date, commercial_revision, tenant_id, customer_account_id, created_by)
                values (?, ?, ?, ?, 'ACTIVE', 'Plan', ?, ?, 0, ?, ?, 'test')
                """, subUuid.toString(), "SUB-" + subUuid, accountId, "Q-" + subUuid,
                TERM_START, TERM_END, tenant, customer));
        return subscriptions.findBySubscriptionNumberAndDeletedFalse("SUB-" + subUuid).orElseThrow();
    }

    private Asset seedAsset(Subscription subscription, String accountId, String contractLineId,
                            String productRef, BigDecimal quantity) {
        return new TransactionTemplate(transactions).execute(status -> assets.save(Asset.builder()
                .uuid(UUID.randomUUID().toString())
                .accountId(accountId)
                .contractLineId(contractLineId)
                .subscriptionId(subscription.getId())
                .productRef(productRef)
                .quantity(quantity)
                .status(AssetStatus.ACTIVE)
                .effectiveStart(TERM_START)
                .effectiveEnd(TERM_END)
                .build()));
    }

    private void seedCompletedAppliedDocumentWithAssets(UUID tenant, UUID documentId, UUID firstSubUuid,
                                                        UUID secondSubUuid, String firstLineUuid,
                                                        String secondLineUuid, ApplyCommand command) {
        UUID contractId = UUID.randomUUID();
        UUID lifecycleId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        String accountId = "acct-replay-" + UUID.randomUUID();

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("""
                    insert into contracts (id, tenant_id, contract_number, customer_account_id, term_model,
                        start_date, end_date, status, last_executed_seq, created_by)
                    values (?, ?, ?, ?, 'TERMED', ?, ?, 'ACTIVE', 1, 'test')
                    """, contractId, tenant, "C-" + contractId, customer, TERM_START, TERM_END);
            jdbc.update("""
                    insert into amendment_documents (
                        id, tenant_id, contract_id, lifecycle_change_id, lifecycle_change_revision, intent,
                        initiation_trigger, provisional_number, amendment_seq, amendment_number, document_kind,
                        status, current_revision_no, executed_revision_no, signature_policy, action_date,
                        contract_effective_date, co_term_date, currency_code, tcv_delta, arr_delta,
                        one_time_total, credit_total, content_digest, evidence_class, created_by)
                    values (?, ?, ?, ?, 1, 'AMEND', 'MANUAL', ?, 1, ?, 'AMENDMENT', 'APPLIED', 1, 1,
                        'NOT_REQUIRED', ?, ?, ?, 'USD', 0, 0, 0, 0, ?, 'STANDARD', 'test')
                    """, documentId, tenant, contractId, lifecycleId, "P-" + documentId, "C-A001",
                    EFFECTIVE_DATE, EFFECTIVE_DATE, TERM_END, HASH);
            jdbc.update("""
                    insert into amendment_document_revisions (
                        document_id, revision_no, tenant_id, lifecycle_change_revision, target_quote_id,
                        content_digest, inputs_snapshot_ref, status_at_close, created_by)
                    values (?, 1, ?, 1, ?, ?, 'snapshot', 'EXECUTED', 'test')
                    """, documentId, tenant, quoteId, HASH);
            for (UUID subUuid : List.of(firstSubUuid, secondSubUuid)) {
                jdbc.update("""
                        insert into subscriptions (uuid, subscription_number, customer_id, quote_id, status,
                            plan_name, start_date, end_date, commercial_revision, contract_id, tenant_id,
                            customer_account_id, created_by)
                        values (?, ?, ?, ?, 'ACTIVE', 'Plan', ?, ?, 1, ?, ?, ?, 'test')
                        """, subUuid.toString(), "SUB-" + subUuid, accountId, "Q-" + subUuid,
                        TERM_START, TERM_END, contractId, tenant, customer);
            }
            jdbc.update("""
                    insert into amendment_application_units (
                        document_id, unit_no, tenant_id, dependency_order, target_type, target_ref,
                        observed_commercial_revision, status, created_by)
                    values (?, 1, ?, 1, 'SUBSCRIPTION', ?, 0, 'APPLIED', 'test'),
                           (?, 2, ?, 2, 'SUBSCRIPTION', ?, 0, 'APPLIED', 'test')
                    """, documentId, tenant, firstSubUuid.toString(), documentId, tenant, secondSubUuid.toString());
            jdbc.update("""
                    insert into amendment_document_lines (
                        id, tenant_id, document_id, revision_no, line_no, change_line_ref, action_code,
                        business_category, apply_target_type, target_subscription_id, source_accepted_line_uuid,
                        quantity_semantics, start_qty, delta_qty, end_qty, effective_from, effective_to,
                        charge_type, price_as_of, product_external_id, pricebook_version, configuration_hash,
                        policy_slot, application_unit_no, created_by)
                    values (?, ?, ?, 1, 1, ?, 'UPDATE', 'UPSELL', 'SUBSCRIPTION', ?, ?, 'ABSOLUTE', 5, 3, 8,
                            ?, ?, 'RECURRING', 'EFFECTIVE_DATE', 'prod-u', 'pb-1', ?, 'AMENDMENT', 1, 'test'),
                           (?, ?, ?, 1, 2, ?, 'REMOVE', 'CANCELLATION', 'SUBSCRIPTION', ?, ?, 'ABSOLUTE', 3, -3, 0,
                            ?, ?, 'RECURRING', 'EFFECTIVE_DATE', 'prod-r', 'pb-1', ?, 'AMENDMENT', 2, 'test')
                    """,
                    UUID.randomUUID(), tenant, documentId, UUID.randomUUID(), firstSubUuid, firstLineUuid,
                    EFFECTIVE_DATE, TERM_END, HASH,
                    UUID.randomUUID(), tenant, documentId, UUID.randomUUID(), secondSubUuid, secondLineUuid,
                    EFFECTIVE_DATE, TERM_END, HASH);

            // Seed already-applied asset rows and baseline asset outbox rows representing the prior apply.
            Subscription firstSub = subscriptions.findBySubscriptionNumberAndDeletedFalse("SUB-" + firstSubUuid).orElseThrow();
            Subscription secondSub = subscriptions.findBySubscriptionNumberAndDeletedFalse("SUB-" + secondSubUuid).orElseThrow();
            Asset firstAsset = assets.save(Asset.builder()
                    .uuid(UUID.randomUUID().toString())
                    .accountId(accountId)
                    .contractLineId(firstLineUuid)
                    .subscriptionId(firstSub.getId())
                    .productRef("prod-u")
                    .quantity(new BigDecimal("8.0000"))
                    .status(AssetStatus.AMENDED)
                    .effectiveStart(TERM_START)
                    .effectiveEnd(TERM_END)
                    .build());
            Asset secondAsset = assets.save(Asset.builder()
                    .uuid(UUID.randomUUID().toString())
                    .accountId(accountId)
                    .contractLineId(secondLineUuid)
                    .subscriptionId(secondSub.getId())
                    .productRef("prod-r")
                    .quantity(new BigDecimal("3.0000"))
                    .status(AssetStatus.TERMINATED)
                    .effectiveStart(TERM_START)
                    .effectiveEnd(EFFECTIVE_DATE)
                    .build());

            jdbc.update("""
                    insert into outbox_events (uuid, aggregate_type, aggregate_id, event_type, topic,
                        event_version, payload, published, status, retry_count, version, is_deleted)
                    values (?, 'Asset', ?, ?, ?, 1, '{}', false, 'PENDING', 0, 0, false),
                           (?, 'Asset', ?, ?, ?, 1, '{}', false, 'PENDING', 0, 0, false)
                    """,
                    UUID.randomUUID().toString(), firstAsset.getUuid(),
                    EventContractRegistry.ASSET_AMENDED_V1.topic(), EventContractRegistry.ASSET_AMENDED_V1.topic(),
                    UUID.randomUUID().toString(), secondAsset.getUuid(),
                    EventContractRegistry.ASSET_TERMINATED_V1.topic(), EventContractRegistry.ASSET_TERMINATED_V1.topic());
        });

        // Compute exact digests/pin fingerprints and record matching applied ledger rows.
        var document = documents.findById(documentId).orElseThrow();
        var lines = gateway.lines(documentId, 1);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (var unit : units.findByDocumentIdOrderByDependencyOrderAscUnitNoAsc(documentId)) {
                String digest = DocumentApplyDigest.unitDigest(documentId, unit,
                        lines.stream().filter(line -> line.unitNo() == unit.getUnitNo()).toList());
                jdbc.update("""
                        update amendment_application_units
                        set apply_key_digest = ?, attempt_revision_no = 1, attempt_pin_fingerprint = ?
                        where document_id = ? and unit_no = ?
                        """, digest, DocumentApplyOrchestrator.pinFingerprint(document, unit, command),
                        documentId, unit.getUnitNo());
                jdbc.update("""
                        insert into document_apply_ledger (tenant_id, document_id, unit_no, apply_key_digest, created_by)
                        values (?, ?, ?, ?, 'earlier')
                        """, tenant, documentId, unit.getUnitNo(), digest);
            }
        });
    }

    private void assertMatchesContract(OutboxRow row, EventContract expected, String expectedAggregateId) {
        assertThat(row.aggregateType()).isEqualTo("Asset");
        assertThat(row.aggregateId()).isEqualTo(expectedAggregateId);
        assertThat(row.topic()).isEqualTo(expected.topic());
        // ADR-018 D4 expand step: outbox_events.event_type retains contract.topic() while topic + event_version carry envelope metadata.
        assertThat(row.eventType()).isEqualTo(expected.topic());
        assertThat(row.eventVersion()).isEqualTo(expected.eventVersion());
        assertThat(row.status()).isEqualTo("PENDING");
        assertThat(row.published()).isFalse();

        EventContract registered = EventContractRegistry.forTopic(row.topic()).orElseThrow();
        assertThat(registered).isEqualTo(expected);
        assertThat(registered.isPublished()).isTrue();
        assertThat(registered.eventType()).isEqualTo(expected.eventType());
        assertThat(registered.eventVersion()).isEqualTo(row.eventVersion());
    }

    private OutboxRow singleOutboxRow(String aggregateId, String topic) {
        List<OutboxRow> rows = jdbc.query("""
                select id, uuid, aggregate_type, aggregate_id, event_type, topic, event_version, payload, status, published
                from outbox_events
                where aggregate_id = ? and topic = ?
                order by id asc
                """,
                (rs, rowNum) -> new OutboxRow(
                        rs.getLong("id"),
                        rs.getString("uuid"),
                        rs.getString("aggregate_type"),
                        rs.getString("aggregate_id"),
                        rs.getString("event_type"),
                        rs.getString("topic"),
                        rs.getInt("event_version"),
                        rs.getString("payload"),
                        rs.getString("status"),
                        rs.getBoolean("published")),
                aggregateId, topic);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private record OutboxRow(long id, String uuid, String aggregateType, String aggregateId,
                             String eventType, String topic, int eventVersion, String payload,
                             String status, boolean published) {
    }
}
