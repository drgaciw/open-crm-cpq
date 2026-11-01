package com.opencrm.contract.documents.apply;

import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.repository.documents.AmendmentApplicationUnitRepository;
import com.opencrm.contract.repository.documents.AmendmentDocumentRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
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
@org.springframework.context.annotation.Import(DocumentApplyIntegrationTest.OsivPreloadConfiguration.class)
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

    private MockMvc mvc;

    @Autowired
    private org.springframework.web.context.WebApplicationContext webContext;

    @Autowired
    private java.util.concurrent.atomic.AtomicReference<MockMvc> testMvc;

    @org.junit.jupiter.api.BeforeEach
    void setupMvc() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        testMvc.set(mvc);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private AmendmentApplicationUnitRepository units;

    @Autowired
    private DocumentApplyGateway gateway;

    @Autowired
    private AmendmentDocumentRepository documents;

    @Autowired
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @Autowired
    private com.opencrm.contract.documents.events.AmendmentEventWriter eventWriter;

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

    @Test
    void pendingMatchingLedgerDoesNotApplyEarlierSibling() {
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        seed(tenant, documentId, UUID.randomUUID(), UUID.randomUUID(), false);
        var first = units.findByDocumentIdOrderByDependencyOrderAscUnitNoAsc(documentId).getFirst();
        String digest = DocumentApplyDigest.unitDigest(documentId, first,
                gateway.lines(documentId, 1).stream().filter(line -> line.unitNo() == 1).toList());
        jdbc.update("update amendment_application_units set dependency_order = 2 where document_id = ? and unit_no = 1",
                documentId);
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
        assertThat(jdbc.queryForList("""
                select status from amendment_application_units where document_id = ? order by unit_no
                """, String.class, documentId)).containsExactly("PENDING", "PENDING");
        assertThat(jdbc.queryForObject("select count(*) from document_apply_ledger where document_id = ?",
                Long.class, documentId)).isEqualTo(1L);
    }

    @Test
    void recoveryFinalizesCommittedUnitsAndWritesOneOutcomeEvent() {
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        seedFullyAppliedUnits(tenant, documentId);
        ApplyCommand command = new ApplyCommand(documentId, tenant, "quote-service", "ROLE_INTERNAL_INVOKE",
                "corr-int", Map.of());

        assertThat(orchestrator.apply(command).status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(orchestrator.apply(command).replayed()).isTrue();
        assertThat(jdbc.queryForObject("select status from amendment_documents where id = ?", String.class, documentId))
                .isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("""
                select count(*) from amendment_document_events where document_id = ? and event_type = 'APPLY_DOCUMENT_APPLIED'
                """, Long.class, documentId)).isEqualTo(1L);
    }

    @Test
    void recoveryRollsBackOutcomeWhenAuditInsertFails() {
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        seedFullyAppliedUnits(tenant, documentId);
        ApplyCommand command = new ApplyCommand(documentId, tenant, "quote-service", "ROLE_INTERNAL_INVOKE",
                "corr-int", Map.of());
        jdbc.execute("alter table amendment_document_events add constraint test_reject_recovery_event check "
                + "(document_id <> '" + documentId + "'::uuid or event_type <> 'APPLY_DOCUMENT_APPLIED')");
        try {
            assertThatThrownBy(() -> orchestrator.apply(command)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("select status from amendment_documents where id = ?", String.class, documentId))
                    .isEqualTo("EXECUTED");
            assertThat(jdbc.queryForObject("""
                    select count(*) from amendment_document_events where document_id = ? and event_type = 'APPLY_DOCUMENT_APPLIED'
                    """, Long.class, documentId)).isZero();
        } finally {
            jdbc.execute("alter table amendment_document_events drop constraint test_reject_recovery_event");
        }
        assertThat(orchestrator.apply(command).status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
    }

    @Test
    void blockedReasonAndObservedPinSurviveReplayAndRejectChangedPin() {
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        seed(tenant, documentId, UUID.randomUUID(), UUID.randomUUID(), false);
        ApplyCommand command = new ApplyCommand(documentId, tenant, "quote-service", "ROLE_INTERNAL_INVOKE",
                "corr-int", Map.of(1, "pb-1", 2, "wrong"));
        ApplyResult first = orchestrator.apply(command);
        assertThat(first.status()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(first.unitOutcomes().get(1).reason()).isEqualTo("V9");
        assertThat(jdbc.queryForObject("select block_reason from amendment_application_units where document_id = ? and unit_no = 2", String.class, documentId)).isEqualTo("V9");
        assertThat(jdbc.queryForObject("select attempt_pin_fingerprint from amendment_application_units where document_id = ? and unit_no = 2", String.class, documentId)).matches("v1:[0-9a-f]{64}");
        // A later refused/speculative block event cannot change the committed unit reason.
        new TransactionTemplate(transactions).executeWithoutResult(status -> eventWriter.appendApplyDecision(
                new com.opencrm.contract.documents.events.AmendmentEventAppend(documentId,
                        DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED, null, null, 1, 2, "quote-service",
                        com.opencrm.contract.domain.documents.ActorType.SERVICE, "ROLE_INTERNAL_INVOKE", java.time.Instant.now(),
                        "DIGEST_CONFLICT", "ab".repeat(32), "speculative", null, "later", "quote-service")));
        assertThat(orchestrator.apply(command).unitOutcomes()).isEqualTo(first.unitOutcomes());
        ApplyCommand changed = new ApplyCommand(documentId, tenant, "quote-service", "ROLE_INTERNAL_INVOKE",
                "corr-int", Map.of(1, "pb-1", 2, "pb-1"));
        assertThatThrownBy(() -> orchestrator.apply(changed)).isInstanceOfSatisfying(DocumentApplyException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT"));
        assertThat(jdbc.queryForObject("select count(*) from document_apply_ledger where document_id = ?", Long.class, documentId)).isEqualTo(1L);
    }

    @Test
    void auditFailureRollsBackBlockReasonAndPinEvidence() {
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        seed(tenant, documentId, UUID.randomUUID(), UUID.randomUUID(), false);
        ApplyCommand command = new ApplyCommand(documentId, tenant, "quote-service", "ROLE_INTERNAL_INVOKE",
                "corr-int", Map.of(2, "wrong"));
        jdbc.execute("alter table amendment_document_events add constraint test_reject_v9_event check "
                + "(document_id <> '" + documentId + "'::uuid or reason_code is distinct from 'V9')");
        try {
            assertThatThrownBy(() -> orchestrator.apply(command)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("select status from amendment_application_units where document_id = ? and unit_no = 2", String.class, documentId)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("select block_reason from amendment_application_units where document_id = ? and unit_no = 2", String.class, documentId)).isNull();
            assertThat(jdbc.queryForObject("select attempt_pin_fingerprint from amendment_application_units where document_id = ? and unit_no = 2", String.class, documentId)).isNull();
        } finally {
            jdbc.execute("alter table amendment_document_events drop constraint test_reject_v9_event");
        }
        assertThat(orchestrator.apply(command).unitOutcomes().get(1).reason()).isEqualTo("V9");
        assertThat(orchestrator.apply(command).replayed()).isTrue();
    }

    @org.junit.jupiter.api.RepeatedTest(5)
    void concurrentIdenticalAttemptsSerializeAndReplayWithoutDuplicateEffects() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        seed(tenant, documentId, UUID.randomUUID(), UUID.randomUUID(), false);
        ApplyCommand command = new ApplyCommand(documentId, tenant, "quote-service", "ROLE_INTERNAL_INVOKE", "corr-int", Map.of());
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var results = new java.util.ArrayList<java.util.concurrent.Future<ApplyResult>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                gateway.lockDocument(documentId);
                for (int i = 0; i < 2; i++) results.add(pool.submit(() -> {
                    // Model OpenEntityManagerInView: the request-bound persistence context survives
                    // the preflight and per-unit REQUIRES_NEW transactions.
                    var entityManager = entityManagerFactory.createEntityManager();
                    org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(
                            entityManagerFactory, new org.springframework.orm.jpa.EntityManagerHolder(entityManager));
                    try { ready.countDown(); return orchestrator.apply(command); }
                    finally {
                        org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(entityManagerFactory);
                        entityManager.close();
                    }
                }));
                try {
                    assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                    while (System.nanoTime() < deadline && jdbc.queryForObject("select count(*) from pg_locks where locktype = 'advisory' and not granted", Long.class) < 2) Thread.sleep(20);
                    assertThat(jdbc.queryForObject("select count(*) from pg_locks where locktype = 'advisory' and not granted", Long.class)).isGreaterThanOrEqualTo(2);
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            });
            var first = results.get(0).get(20, java.util.concurrent.TimeUnit.SECONDS);
            var second = results.get(1).get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(first.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
            assertThat(second.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
            assertThat(jdbc.queryForObject("select count(*) from document_apply_ledger where document_id = ?", Long.class, documentId)).isEqualTo(2L);
            assertThat(jdbc.queryForObject("select count(*) from amendment_document_events where document_id = ? and event_type = 'APPLY_DOCUMENT_APPLIED'", Long.class, documentId)).isEqualTo(1L);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void requestBoundPersistenceContextRefreshesPreloadedDocumentAndUnitsAfterAnotherCommit() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        seed(tenant, documentId, UUID.randomUUID(), UUID.randomUUID(), false);
        ApplyCommand command = new ApplyCommand(documentId, tenant, "quote-service", "ROLE_INTERNAL_INVOKE", "corr-int", Map.of());
        var entityManager = entityManagerFactory.createEntityManager();
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(
                entityManagerFactory, new org.springframework.orm.jpa.EntityManagerHolder(entityManager));
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            assertThat(documents.findById(documentId).orElseThrow().getStatus()).isEqualTo(AmendmentDocumentStatus.EXECUTED);
            assertThat(units.findByDocumentIdOrderByDependencyOrderAscUnitNoAsc(documentId)).allMatch(
                    unit -> unit.getStatus() == com.opencrm.contract.domain.documents.ApplicationUnitStatus.PENDING);
            assertThat(pool.submit(() -> orchestrator.apply(command)).get(20, java.util.concurrent.TimeUnit.SECONDS).status())
                    .isEqualTo(AmendmentDocumentStatus.APPLIED);
            ApplyResult replay = orchestrator.apply(command);
            assertThat(replay.replayed()).isTrue();
            assertThat(replay.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        } finally {
            pool.shutdownNow();
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            entityManager.close();
        }
    }

    @org.junit.jupiter.api.RepeatedTest(5)
    void concurrentHttpRequestsUseFreshLockedStateUnderOpenEntityManagerInView() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        seed(tenant, documentId, UUID.randomUUID(), UUID.randomUUID(), false);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> request = () -> {
                ready.countDown();
                if (!ready.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("request barrier timed out");
                return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/internal/v1/amendments/{id}:apply", documentId)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("quote-service").claim("tid", tenant.toString()))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INTERNAL_INVOKE"))))
                        .andReturn().getResponse().getStatus();
            };
            var one = pool.submit(request);
            var two = pool.submit(request);
            assertThat(one.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(two.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(jdbc.queryForObject("select count(*) from document_apply_ledger where document_id = ?", Long.class, documentId)).isEqualTo(2L);
            assertThat(jdbc.queryForObject("select count(*) from amendment_document_events where document_id = ? and event_type = 'APPLY_DOCUMENT_APPLIED'", Long.class, documentId)).isEqualTo(1L);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void httpRequestRefreshesEntitiesPreloadedBeforeConcurrentHttpCommit() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        seed(tenant, documentId, UUID.randomUUID(), UUID.randomUUID(), false);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/internal/v1/amendments/{id}:apply", documentId)
                .header("X-Test-Preload", tenant.toString())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.subject("quote-service").claim("tid", tenant.toString()))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INTERNAL_INVOKE"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.replayed").value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.status").value("APPLIED"));
        assertThat(jdbc.queryForObject("select count(*) from document_apply_ledger where document_id = ?", Long.class, documentId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(*) from amendment_document_events where document_id = ? and event_type = 'APPLY_DOCUMENT_APPLIED'", Long.class, documentId)).isEqualTo(1L);
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class OsivPreloadConfiguration {
        @org.springframework.context.annotation.Bean
        java.util.concurrent.atomic.AtomicReference<MockMvc> testMvc() {
            return new java.util.concurrent.atomic.AtomicReference<>();
        }

        @org.springframework.context.annotation.Bean
        org.springframework.web.servlet.config.annotation.WebMvcConfigurer preloadInterceptor(
                AmendmentDocumentRepository documents, AmendmentApplicationUnitRepository units,
                jakarta.persistence.EntityManagerFactory factory, java.util.concurrent.atomic.AtomicReference<MockMvc> testMvc) {
            return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer() {
                @Override
                public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
                    registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
                        @Override
                        public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                                 jakarta.servlet.http.HttpServletResponse response, Object handler) throws Exception {
                            String tenant = request.getHeader("X-Test-Preload");
                            if (tenant == null) return true;
                            String uri = request.getRequestURI();
                            UUID id = UUID.fromString(uri.substring(uri.lastIndexOf('/') + 1, uri.indexOf(':')));
                            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(factory)).isTrue();
                            assertThat(documents.findById(id).orElseThrow().getStatus()).isEqualTo(AmendmentDocumentStatus.EXECUTED);
                            assertThat(units.findByDocumentIdOrderByDependencyOrderAscUnitNoAsc(id)).allMatch(
                                    unit -> unit.getStatus() == com.opencrm.contract.domain.documents.ApplicationUnitStatus.PENDING);
                            var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
                            try {
                                var result = worker.submit(() -> testMvc.get().perform(
                                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/amendments/{id}:apply", id)
                                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                                        .jwt(jwt -> jwt.subject("quote-service").claim("tid", tenant))
                                                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INTERNAL_INVOKE"))))
                                        .andReturn().getResponse().getStatus());
                                assertThat(result.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
                            } finally { worker.shutdownNow(); }
                            return true;
                        }
                    }).order(Integer.MAX_VALUE);
                }
            };
        }
    }

    @Test
    void foreignTenantLedgerForPendingUnitFailsBeforeAnySiblingWrites() {
        UUID tenant = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        seed(tenant, documentId, UUID.randomUUID(), UUID.randomUUID(), false);
        jdbc.update("update amendment_application_units set dependency_order = 2 where document_id = ? and unit_no = 1", documentId);
        jdbc.update("update amendment_application_units set dependency_order = 1 where document_id = ? and unit_no = 2", documentId);
        jdbc.update("insert into document_apply_ledger (tenant_id, document_id, unit_no, apply_key_digest, created_by) values (?, ?, 1, ?, 'other-tenant')",
                UUID.randomUUID(), documentId, "ab".repeat(32));
        ApplyCommand command = new ApplyCommand(documentId, tenant, "quote-service", "ROLE_INTERNAL_INVOKE", "corr-int", Map.of());
        assertThatThrownBy(() -> orchestrator.apply(command)).isInstanceOfSatisfying(DocumentApplyException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT"));
        assertThat(jdbc.queryForList("select status from amendment_application_units where document_id = ? order by unit_no", String.class, documentId))
                .containsExactly("PENDING", "PENDING");
        assertThat(jdbc.queryForObject("select count(*) from subscription_rate_periods where amendment_document_line_id in (select id from amendment_document_lines where document_id = ?)", Long.class, documentId)).isZero();
    }

    private void seedFullyAppliedUnits(UUID tenant, UUID documentId) {
        seed(tenant, documentId, UUID.randomUUID(), UUID.randomUUID(), false);
        var lines = gateway.lines(documentId, 1);
        var document = documents.findById(documentId).orElseThrow();
        var command = new ApplyCommand(documentId, tenant, "quote-service", "ROLE_INTERNAL_INVOKE", "corr-int", Map.of());
        for (var unit : units.findByDocumentIdOrderByDependencyOrderAscUnitNoAsc(documentId)) {
            String digest = DocumentApplyDigest.unitDigest(documentId, unit,
                    lines.stream().filter(line -> line.unitNo() == unit.getUnitNo()).toList());
            jdbc.update("update amendment_application_units set status = 'APPLIED', apply_key_digest = ?, "
                    + "attempt_revision_no = 1, attempt_pin_fingerprint = ? where document_id = ? and unit_no = ?",
                    digest, DocumentApplyOrchestrator.pinFingerprint(document, unit, command), documentId, unit.getUnitNo());
            jdbc.update("""
                    insert into document_apply_ledger (tenant_id, document_id, unit_no, apply_key_digest, created_by)
                    values (?, ?, ?, ?, 'earlier')
                    """, tenant, documentId, unit.getUnitNo(), digest);
        }
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
