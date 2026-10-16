package com.opencrm.contract.documents;

import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.ActorType;
import com.opencrm.contract.domain.documents.AmendmentApplicationUnit;
import com.opencrm.contract.domain.documents.AmendmentDocument;
import com.opencrm.contract.domain.documents.AmendmentDocumentEvent;
import com.opencrm.contract.domain.documents.AmendmentDocumentLine;
import com.opencrm.contract.domain.documents.AmendmentRevisionKey;
import com.opencrm.contract.domain.documents.AmendmentDocumentRevision;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.AmendmentIntent;
import com.opencrm.contract.domain.documents.ApplicationUnitStatus;
import com.opencrm.contract.domain.documents.ApplyTargetType;
import com.opencrm.contract.domain.documents.BusinessCategory;
import com.opencrm.contract.domain.documents.ChargeType;
import com.opencrm.contract.domain.documents.ContractHeader;
import com.opencrm.contract.domain.documents.ContractStatus;
import com.opencrm.contract.domain.documents.DocumentKind;
import com.opencrm.contract.domain.documents.EvidenceClass;
import com.opencrm.contract.domain.documents.InitiationTrigger;
import com.opencrm.contract.domain.documents.PolicySlot;
import com.opencrm.contract.domain.documents.PriceAsOf;
import com.opencrm.contract.domain.documents.QuantitySemantics;
import com.opencrm.contract.domain.documents.RevisionCloseStatus;
import com.opencrm.contract.domain.documents.SignaturePolicy;
import com.opencrm.contract.domain.documents.TermModel;
import com.opencrm.contract.repository.documents.AmendmentApplicationUnitRepository;
import com.opencrm.contract.repository.documents.AmendmentDocumentEventRepository;
import com.opencrm.contract.repository.documents.AmendmentDocumentLineRepository;
import com.opencrm.contract.repository.documents.AmendmentDocumentRepository;
import com.opencrm.contract.repository.documents.AmendmentDocumentRevisionRepository;
import com.opencrm.contract.repository.documents.ContractHeaderRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DRG-579 acceptance: V1_7 columns, expand-only compatibility, V11, A3 and append-only events.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false"})
class ContractDocumentsSchemaIntegrationTest {

    private static final String DIGEST = digest("document");
    private static final String OTHER_DIGEST = digest("other-document");
    private static final String APP_ROLE = "cam_app_it";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(384L * 1024 * 1024));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> schemaUrl(POSTGRES, "contract"));
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ContractHeaderRepository contracts;
    @Autowired AmendmentDocumentRepository documents;
    @Autowired AmendmentDocumentRevisionRepository revisions;
    @Autowired AmendmentApplicationUnitRepository units;
    @Autowired AmendmentDocumentLineRepository lines;
    @Autowired AmendmentDocumentEventRepository events;

    @Test
    void acS11_tablesMatchFieldSchemaNullabilityAndUniqueness() {
        Map<String, String> actual = new HashMap<>();
        jdbc.query("""
                SELECT table_name, column_name, is_nullable, data_type
                FROM information_schema.columns
                WHERE table_schema = 'contract'
                  AND table_name IN (
                    'contracts', 'amendment_documents', 'amendment_document_revisions',
                    'amendment_document_lines', 'amendment_application_units', 'amendment_document_events')
                """, rs -> {
            actual.put(rs.getString("table_name") + "." + rs.getString("column_name"),
                    rs.getString("is_nullable") + " " + rs.getString("data_type"));
        });
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expectedColumns());
        assertThat(jdbc.queryForObject("""
                SELECT is_nullable || ' ' || data_type FROM information_schema.columns
                WHERE table_schema = 'contract' AND table_name = 'subscriptions' AND column_name = 'contract_id'
                """, String.class)).isEqualTo("YES uuid");
        var rateColumns = jdbc.queryForList("""
                SELECT column_name, is_nullable, data_type FROM information_schema.columns
                WHERE table_schema = 'contract' AND table_name = 'subscription_rate_periods'
                  AND column_name IN ('amendment_document_line_id', 'revision_no')
                ORDER BY column_name
                """);
        assertThat(rateColumns).extracting(row -> row.get("column_name") + " " + row.get("is_nullable") + " " + row.get("data_type"))
                .containsExactly("amendment_document_line_id YES uuid", "revision_no YES integer");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'contract' AND table_name = 'applied_quote_acceptances'
                  AND column_name = 'amendment_document_id'
                """, Long.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'contract' AND table_name = 'contracts' AND column_name = 'precedence_flag'
                """, Long.class)).isZero();
        assertThat(indexNames()).contains(
                "uq_contracts_tenant_number", "uq_amendment_document_lifecycle", "uq_amendment_document_seq",
                "uq_amendment_document_request", "uq_amendment_document_envelope",
                "uq_amendment_revision_lifecycle", "uq_amendment_revision_one_executed",
                "uq_amendment_line_triple", "uq_amendment_line_source",
                "uq_amendment_event_seq", "uq_amendment_event_content_hash", "uq_amendment_event_source",
                "idx_amendment_document_contract_status", "idx_amendment_document_status_expires",
                "idx_amendment_document_retention", "idx_subscriptions_contract_id", "idx_rate_period_amendment_line");
        assertThat(jdbc.queryForObject("""
                SELECT pg_get_expr(i.indpred, i.indrelid)
                FROM pg_index i
                JOIN pg_class c ON c.oid = i.indexrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'contract' AND c.relname = 'uq_amendment_revision_one_executed'
                """, String.class)).contains("EXECUTED");
    }

    @Test
    void acS12_rowsWrittenBeforeAndAfterV17KeepThePreviousShape() throws Exception {
        try (var baseline = new PostgreSQLContainer<>("postgres:16-alpine")) {
            baseline.start();
            String url = schemaUrl(baseline, "s1_from_v16");
            Flyway.configure().dataSource(url, baseline.getUsername(), baseline.getPassword())
                    .defaultSchema("s1_from_v16").schemas("s1_from_v16").locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("1.6")).load().migrate();
            try (Connection connection = DriverManager.getConnection(url, baseline.getUsername(), baseline.getPassword())) {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO subscriptions(uuid, subscription_number, customer_id, quote_id, plan_name,
                            start_date, end_date, mrr, arr, currency_code, status)
                        VALUES ('11111111-1111-4111-8111-000000000579','SUB-579','C-579','Q-579','Plan',
                            '2026-01-01','2026-12-31',10,120,'USD','ACTIVE')
                        RETURNING id
                        """)) {
                    try (ResultSet keys = insert.executeQuery()) {
                        assertThat(keys.next()).isTrue();
                        long subscriptionId = keys.getLong(1);
                        try (PreparedStatement rate = connection.prepareStatement("""
                                INSERT INTO subscription_rate_periods(
                                    uuid, subscription_id, accepted_line_uuid, sequence, start_date,
                                    billing_anchor_date, end_date, effective_end_date, provenance, created_by)
                                VALUES ('22222222-2222-4222-8222-000000000579', ?, 'line-579', 1,
                                    '2026-01-01','2026-01-01','2026-12-31','2026-12-31','ACCEPTED_PRICED','s1')
                                """)) {
                            rate.setLong(1, subscriptionId);
                            assertThat(rate.executeUpdate()).isEqualTo(1);
                        }
                    }
                }
            }
            Flyway.configure().dataSource(url, baseline.getUsername(), baseline.getPassword())
                    .defaultSchema("s1_from_v16").schemas("s1_from_v16").locations("classpath:db/migration")
                    .load().migrate();
            try (Connection connection = DriverManager.getConnection(url, baseline.getUsername(), baseline.getPassword());
                 PreparedStatement look = connection.prepareStatement("""
                         SELECT s.contract_id, p.amendment_document_line_id, p.revision_no, p.provenance
                         FROM subscriptions s
                         JOIN subscription_rate_periods p ON p.subscription_id = s.id
                         WHERE s.subscription_number = 'SUB-579'
                         """);
                 ResultSet rows = look.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("contract_id")).isNull();
                assertThat(rows.getObject("amendment_document_line_id")).isNull();
                assertThat(rows.getObject("revision_no")).isNull();
                assertThat(rows.getString("provenance")).isEqualTo("ACCEPTED_PRICED");
            }
            try (Connection connection = DriverManager.getConnection(url, baseline.getUsername(), baseline.getPassword());
                 PreparedStatement insert = connection.prepareStatement("""
                         INSERT INTO subscriptions(uuid, subscription_number, customer_id, quote_id, plan_name,
                             start_date, end_date, status)
                         VALUES ('33333333-3333-4333-8333-000000000579','SUB-579-after','C-579','Q-579b','Plan',
                             '2026-02-01','2026-12-31','PENDING')
                         RETURNING contract_id
                         """);
                 ResultSet keys = insert.executeQuery()) {
                assertThat(keys.next()).isTrue();
                assertThat(keys.getObject(1)).isNull();
            }
        }
    }

    @Test
    void acS13_secondExecutedRevisionIsRejected() {
        UUID contractId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        assertThatThrownBy(() -> withTransaction(POSTGRES, "contract", connection -> {
            insertContract(connection, contractId, "C-V11");
            insertDocument(connection, documentId, contractId, UUID.randomUUID(), null, null);
            insertRevision(connection, documentId, 1, 1, "EXECUTED");
            insertRevision(connection, documentId, 2, 2, "EXECUTED");
        })).isInstanceOf(SQLException.class).hasMessageContaining("uq_amendment_revision_one_executed");
    }

    @Test
    void acS14_duplicateAmendmentSeqIsRejected() {
        UUID contractId = UUID.randomUUID();
        assertThatThrownBy(() -> withTransaction(POSTGRES, "contract", connection -> {
            insertContract(connection, contractId, "C-A3");
            UUID first = UUID.randomUUID();
            insertDocument(connection, first, contractId, UUID.randomUUID(), 1, "C-A3-A001");
            insertRevision(connection, first, 1, 1, "OPEN");
            UUID second = UUID.randomUUID();
            insertDocument(connection, second, contractId, UUID.randomUUID(), 1, "C-A3-A001-dup");
        })).isInstanceOf(SQLException.class).hasMessageContaining("uq_amendment_document_seq");
    }

    @Test
    void acS15_appRoleAndTriggerRejectEventUpdateAndDelete() throws Exception {
        UUID contractId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        withTransaction(POSTGRES, "contract", connection -> {
            insertContract(connection, contractId, "C-A2");
            insertDocument(connection, documentId, contractId, UUID.randomUUID(), null, null);
            insertRevision(connection, documentId, 1, 1, "OPEN");
            insertEvent(connection, eventId, documentId, 1, DIGEST, OTHER_DIGEST);
        });
        jdbc.execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + APP_ROLE
                + "') THEN CREATE ROLE " + APP_ROLE + " LOGIN PASSWORD 'cam-app-test'; END IF; END $$");
        jdbc.execute("GRANT cam_app TO " + APP_ROLE);
        try (Connection app = DriverManager.getConnection(schemaUrl(POSTGRES, "contract"), APP_ROLE, "cam-app-test")) {
            UUID appended = UUID.randomUUID();
            insertEvent(app, appended, documentId, 2, OTHER_DIGEST, DIGEST);
            assertThatThrownBy(() -> updateEvent(app, eventId)).isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> deleteEvent(app, eventId)).isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE amendment_document_events SET reason_code = 'tamper' WHERE event_id = ?", eventId))
                .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM amendment_document_events WHERE event_id = ?", eventId))
                .rootCause().hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM amendment_document_events WHERE document_id = ?", Long.class, documentId))
                .isEqualTo(2L);
    }

    @Test
    void entityRoundTripPersistsTheDocumentGraph() {
        UUID tenant = UUID.randomUUID();
        UUID lifecycleChangeId = UUID.randomUUID();
        UUID targetSubscription = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            ContractHeader header = contracts.saveAndFlush(ContractHeader.builder()
                    .tenantId(tenant)
                    .contractNumber("C-ROUND")
                    .customerAccountId(UUID.randomUUID())
                    .termModel(TermModel.TERMED)
                    .startDate(LocalDate.of(2026, 1, 1))
                    .endDate(LocalDate.of(2026, 12, 31))
                    .status(ContractStatus.ACTIVE)
                    .createdBy("s1-test")
                    .build());
            AmendmentDocument document = documents.saveAndFlush(AmendmentDocument.builder()
                    .tenantId(tenant)
                    .contractId(header.getId())
                    .lifecycleChangeId(lifecycleChangeId)
                    .lifecycleChangeRevision(1)
                    .intent(AmendmentIntent.AMEND)
                    .initiationTrigger(InitiationTrigger.MANUAL)
                    .provisionalNumber("C-ROUND-P001")
                    .documentKind(DocumentKind.AMENDMENT)
                    .status(AmendmentDocumentStatus.DRAFT)
                    .currentRevisionNo(1)
                    .signaturePolicy(SignaturePolicy.NOT_REQUIRED)
                    .actionDate(LocalDate.of(2026, 6, 1))
                    .contractEffectiveDate(LocalDate.of(2026, 6, 1))
                    .currencyCode("USD")
                    .tcvDelta(BigDecimal.ZERO)
                    .arrDelta(BigDecimal.ZERO)
                    .oneTimeTotal(BigDecimal.ZERO)
                    .creditTotal(BigDecimal.ZERO)
                    .contentDigest(DIGEST)
                    .evidenceClass(EvidenceClass.STANDARD)
                    .createdBy("s1-test")
                    .build());
            revisions.saveAndFlush(AmendmentDocumentRevision.builder()
                    .tenantId(tenant)
                    .documentId(document.getId())
                    .revisionNo(1)
                    .lifecycleChangeRevision(1)
                    .targetQuoteId(UUID.randomUUID())
                    .contentDigest(DIGEST)
                    .inputsSnapshotRef("snap-1")
                    .statusAtClose(RevisionCloseStatus.OPEN)
                    .createdBy("s1-test")
                    .build());
            units.saveAndFlush(AmendmentApplicationUnit.builder()
                    .tenantId(tenant)
                    .documentId(document.getId())
                    .unitNo(1)
                    .dependencyOrder(1)
                    .targetType(ApplyTargetType.SUBSCRIPTION)
                    .targetRef(targetSubscription.toString())
                    .observedCommercialRevision(0L)
                    .status(ApplicationUnitStatus.PENDING)
                    .createdBy("s1-test")
                    .build());
            lines.saveAndFlush(AmendmentDocumentLine.builder()
                    .tenantId(tenant)
                    .documentId(document.getId())
                    .revisionNo(1)
                    .lineNo(1)
                    .changeLineRef(UUID.randomUUID())
                    .actionCode(ActionCode.ADD)
                    .businessCategory(BusinessCategory.UPSELL)
                    .applyTargetType(ApplyTargetType.SUBSCRIPTION)
                    .targetSubscriptionId(targetSubscription)
                    .quantitySemantics(QuantitySemantics.DELTA)
                    .startQty(BigDecimal.ZERO)
                    .deltaQty(BigDecimal.ONE)
                    .endQty(BigDecimal.ONE)
                    .effectiveFrom(LocalDate.of(2026, 6, 1))
                    .effectiveTo(LocalDate.of(2026, 12, 31))
                    .chargeType(ChargeType.RECURRING)
                    .priceAsOf(PriceAsOf.EFFECTIVE_DATE)
                    .productExternalId("prod-1")
                    .pricebookVersion("pb-1")
                    .configurationHash(DIGEST)
                    .policySlot(PolicySlot.AMENDMENT)
                    .applicationUnitNo(1)
                    .createdBy("s1-test")
                    .build());
            events.saveAndFlush(AmendmentDocumentEvent.builder()
                    .tenantId(tenant)
                    .documentId(document.getId())
                    .seqNo(1L)
                    .eventType("AmendmentDrafted")
                    .toStatus(AmendmentDocumentStatus.DRAFT)
                    .actorId("seller-1")
                    .actorType(ActorType.USER)
                    .actorRole("seller")
                    .occurredAt(Instant.parse("2026-06-01T12:00:00Z"))
                    .payloadDigest(digest("round-payload"))
                    .payloadRef("payload-1")
                    .prevHash(digest("round-prev"))
                    .contentHash(digest("round-content"))
                    .correlationId("corr-round")
                    .createdBy("s1-test")
                    .build());
        });
        AmendmentDocument stored = documents.findByLifecycleChangeId(lifecycleChangeId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AmendmentDocumentStatus.DRAFT);
        assertThat(stored.getAmendmentSeq()).isNull();
        assertThat(revisions.findById(new AmendmentRevisionKey(stored.getId(), 1)))
                .isPresent();
        assertThat(lines.findAll()).anyMatch(line -> stored.getId().equals(line.getDocumentId()));
        AmendmentDocumentEvent storedEvent = events.findAll().stream()
                .filter(event -> stored.getId().equals(event.getDocumentId()))
                .findFirst().orElseThrow();
        assertThat(storedEvent.getRecordedAt()).isNotNull();
        assertThat(contracts.findByTenantIdAndContractNumber(tenant, "C-ROUND")).isPresent();
    }

    private static Map<String, String> expectedColumns() {
        Map<String, String> columns = new HashMap<>();
        put(columns, "contracts", "NO uuid", "id", "tenant_id", "customer_account_id");
        put(columns, "contracts", "NO character varying", "contract_number", "term_model", "status", "created_by");
        put(columns, "contracts", "NO date", "start_date");
        put(columns, "contracts", "YES date", "end_date");
        put(columns, "contracts", "YES character varying", "governing_agreement_ref", "updated_by");
        put(columns, "contracts", "NO integer", "last_executed_seq", "version");
        put(columns, "contracts", "YES uuid", "prior_contract_id");
        put(columns, "contracts", "NO timestamp with time zone", "created_at");
        put(columns, "contracts", "YES timestamp with time zone", "updated_at");

        put(columns, "amendment_documents", "NO uuid", "id", "tenant_id", "contract_id", "lifecycle_change_id");
        put(columns, "amendment_documents", "YES uuid",
                "supersedes_document_id", "superseded_by_document_id", "voids_document_id");
        put(columns, "amendment_documents", "NO integer", "lifecycle_change_revision", "current_revision_no", "version");
        put(columns, "amendment_documents", "YES integer", "amendment_seq", "executed_revision_no");
        put(columns, "amendment_documents", "NO character varying",
                "intent", "initiation_trigger", "provisional_number", "document_kind", "status", "signature_policy",
                "currency_code", "content_digest", "evidence_class", "created_by");
        put(columns, "amendment_documents", "YES character varying",
                "request_identifier", "amendment_number", "approval_ref", "signature_envelope_ref",
                "backdating_reason_code", "rendition_digest", "signed_copy_sha256", "template_id", "template_version",
                "updated_by");
        put(columns, "amendment_documents", "NO date", "action_date", "contract_effective_date");
        put(columns, "amendment_documents", "YES date",
                "service_activation_date", "customer_acceptance_date", "co_term_date", "retention_until");
        put(columns, "amendment_documents", "NO numeric", "tcv_delta", "arr_delta", "one_time_total", "credit_total");
        put(columns, "amendment_documents", "NO boolean", "legal_hold", "is_deleted");
        put(columns, "amendment_documents", "YES timestamp with time zone", "expires_at", "executed_at", "applied_at", "updated_at");
        put(columns, "amendment_documents", "NO timestamp with time zone", "created_at");

        put(columns, "amendment_document_revisions", "NO uuid", "document_id", "tenant_id", "target_quote_id");
        put(columns, "amendment_document_revisions", "NO integer",
                "revision_no", "lifecycle_change_revision", "version");
        put(columns, "amendment_document_revisions", "NO character varying",
                "content_digest", "inputs_snapshot_ref", "status_at_close", "created_by");
        put(columns, "amendment_document_revisions", "YES character varying", "updated_by");
        put(columns, "amendment_document_revisions", "YES timestamp with time zone", "frozen_at", "updated_at");
        put(columns, "amendment_document_revisions", "NO timestamp with time zone", "created_at");

        put(columns, "amendment_application_units", "NO uuid", "document_id", "tenant_id");
        put(columns, "amendment_application_units", "NO integer", "unit_no", "dependency_order", "version");
        put(columns, "amendment_application_units", "NO bigint", "observed_commercial_revision");
        put(columns, "amendment_application_units", "YES bigint", "applied_commercial_revision");
        put(columns, "amendment_application_units", "NO character varying",
                "target_type", "target_ref", "status", "created_by");
        put(columns, "amendment_application_units", "YES character varying", "apply_key_digest", "updated_by");
        put(columns, "amendment_application_units", "NO timestamp with time zone", "created_at");
        put(columns, "amendment_application_units", "YES timestamp with time zone", "updated_at");

        put(columns, "amendment_document_lines", "NO uuid", "id", "tenant_id", "document_id", "change_line_ref");
        put(columns, "amendment_document_lines", "YES uuid", "target_subscription_id");
        put(columns, "amendment_document_lines", "NO integer", "revision_no", "line_no", "application_unit_no", "version");
        put(columns, "amendment_document_lines", "NO character varying",
                "action_code", "business_category", "apply_target_type", "quantity_semantics", "charge_type",
                "price_as_of", "product_external_id", "pricebook_version", "configuration_hash", "policy_slot",
                "created_by");
        put(columns, "amendment_document_lines", "YES character varying",
                "source_accepted_line_uuid", "result_accepted_line_uuid", "metric", "policy_id", "policy_version",
                "policy_decision_id", "pricing_trace_id", "override_reason_code", "override_actor", "updated_by");
        put(columns, "amendment_document_lines", "NO numeric", "start_qty", "delta_qty", "end_qty");
        put(columns, "amendment_document_lines", "YES numeric", "unit_price", "net_amount", "override_value");
        put(columns, "amendment_document_lines", "NO date", "effective_from");
        put(columns, "amendment_document_lines", "YES date", "effective_to");
        put(columns, "amendment_document_lines", "YES jsonb", "segment_schedule");
        put(columns, "amendment_document_lines", "NO timestamp with time zone", "created_at");
        put(columns, "amendment_document_lines", "YES timestamp with time zone", "updated_at");

        put(columns, "amendment_document_events", "NO uuid", "event_id", "tenant_id", "document_id");
        put(columns, "amendment_document_events", "YES uuid", "source_event_id");
        put(columns, "amendment_document_events", "NO bigint", "seq_no");
        put(columns, "amendment_document_events", "YES integer", "revision_no", "unit_no");
        put(columns, "amendment_document_events", "NO character varying",
                "event_type", "actor_id", "actor_type", "actor_role", "payload_digest", "payload_ref",
                "prev_hash", "content_hash", "correlation_id", "created_by");
        put(columns, "amendment_document_events", "YES character varying", "from_status", "to_status", "reason_code");
        put(columns, "amendment_document_events", "NO timestamp with time zone", "occurred_at", "recorded_at", "created_at");
        return columns;
    }

    private static void put(Map<String, String> columns, String table, String type, String... names) {
        for (String name : names) {
            columns.put(table + "." + name, type);
        }
    }

    private Set<String> indexNames() {
        return new HashSet<>(jdbc.queryForList("""
                SELECT c.relname FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'contract' AND c.relkind = 'i'
                """, String.class));
    }

    private static String schemaUrl(PostgreSQLContainer<?> postgres, String schema) {
        String url = postgres.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }

    private void withTransaction(PostgreSQLContainer<?> postgres, String schema, SqlConsumer work) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                schemaUrl(postgres, schema), postgres.getUsername(), postgres.getPassword())) {
            connection.setAutoCommit(false);
            try {
                work.accept(connection);
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private static void insertContract(Connection connection, UUID id, String number) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO contracts(id, tenant_id, contract_number, customer_account_id, term_model,
                    start_date, end_date, status, last_executed_seq, created_by)
                VALUES (?, ?, ?, ?, 'TERMED', ?, ?, 'ACTIVE', 0, 's1-test')
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, UUID.randomUUID());
            statement.setString(3, number);
            statement.setObject(4, UUID.randomUUID());
            statement.setDate(5, Date.valueOf("2026-01-01"));
            statement.setDate(6, Date.valueOf("2026-12-31"));
            statement.executeUpdate();
        }
    }

    private static void insertDocument(Connection connection, UUID id, UUID contractId, UUID lifecycleId,
                                       Integer seq, String number) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO amendment_documents(id, tenant_id, contract_id, lifecycle_change_id,
                    lifecycle_change_revision, intent, initiation_trigger, provisional_number, amendment_seq,
                    amendment_number, document_kind, status, current_revision_no, signature_policy, action_date,
                    contract_effective_date, currency_code, tcv_delta, arr_delta, one_time_total, credit_total,
                    content_digest, evidence_class, legal_hold, is_deleted, created_by)
                VALUES (?, ?, ?, ?, 1, 'AMEND', 'MANUAL', ?, ?, ?, 'AMENDMENT', 'DRAFT', 1, 'NOT_REQUIRED',
                    ?, ?, 'USD', 0, 0, 0, 0, ?, 'STANDARD', false, false, 's1-test')
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, contractId);
            statement.setObject(4, lifecycleId);
            statement.setString(5, "P-" + id.toString().substring(0, 8));
            if (seq == null) {
                statement.setObject(6, null);
                statement.setObject(7, null);
            } else {
                statement.setInt(6, seq);
                statement.setString(7, number);
            }
            statement.setDate(8, Date.valueOf("2026-06-01"));
            statement.setDate(9, Date.valueOf("2026-06-01"));
            statement.setString(10, DIGEST);
            statement.executeUpdate();
        }
    }

    private static void insertRevision(Connection connection, UUID documentId, int revisionNo, int lifecycleRevision,
                                       String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO amendment_document_revisions(document_id, revision_no, tenant_id,
                    lifecycle_change_revision, target_quote_id, content_digest, inputs_snapshot_ref,
                    status_at_close, created_by)
                VALUES (?, ?, ?, ?, ?, ?, 'snap', ?, 's1-test')
                """)) {
            statement.setObject(1, documentId);
            statement.setInt(2, revisionNo);
            statement.setObject(3, UUID.randomUUID());
            statement.setInt(4, lifecycleRevision);
            statement.setObject(5, UUID.randomUUID());
            statement.setString(6, revisionNo == 1 ? DIGEST : OTHER_DIGEST);
            statement.setString(7, status);
            statement.executeUpdate();
        }
    }

    private static void insertEvent(Connection connection, UUID eventId, UUID documentId, int seq,
                                    String prevHash, String contentHash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO amendment_document_events(event_id, tenant_id, document_id, seq_no, event_type,
                    actor_id, actor_type, actor_role, occurred_at, payload_digest, payload_ref, prev_hash,
                    content_hash, correlation_id, created_by)
                VALUES (?, ?, ?, ?, 'AmendmentDrafted', 'seller-1', 'USER', 'seller', now(),
                    ?, 'payload', ?, ?, ?, 's1-test')
                """)) {
            statement.setObject(1, eventId);
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, documentId);
            statement.setLong(4, seq);
            statement.setString(5, DIGEST);
            statement.setString(6, prevHash);
            statement.setString(7, contentHash);
            statement.setString(8, "corr-" + seq);
            statement.executeUpdate();
        }
    }

    private static void updateEvent(Connection connection, UUID eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE amendment_document_events SET reason_code = 'tamper' WHERE event_id = ?")) {
            statement.setObject(1, eventId);
            statement.executeUpdate();
        }
    }

    private static void deleteEvent(Connection connection, UUID eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM amendment_document_events WHERE event_id = ?")) {
            statement.setObject(1, eventId);
            statement.executeUpdate();
        }
    }

    private static String digest(String seed) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
