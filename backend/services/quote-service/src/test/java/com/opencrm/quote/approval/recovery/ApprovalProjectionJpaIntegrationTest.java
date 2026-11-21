package com.opencrm.quote.approval.recovery;

import com.opencrm.quote.approval.*;
import com.opencrm.quote.client.*;
import com.opencrm.quote.quote.*;
import com.opencrm.quote.ramp.RampScheduleGuard;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** Real quote JPA transactions against PostgreSQL, independent canonical-routing test double. */
@Tag("integration")
class ApprovalProjectionJpaIntegrationTest {
    private static PostgreSQLContainer<?> postgres;
    private static EntityManagerFactory emf;
    private static TransactionTemplate tx;
    private static QuoteRepository quotes;
    private ApprovalRoutingClient routing;
    private ApprovalService submitter;
    private ApprovalProjectionWriter writer;
    private Quote initial;
    private final Map<String, RoutingApprovalDto> receipts = new HashMap<>();

    @BeforeAll static void database() {
        String url = System.getProperty("projection.test.jdbc");
        DriverManagerDataSource data;
        if (url == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine"); postgres.start();
            data = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        } else data = new DriverManagerDataSource(url, "receipt_test", "");
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(data);
        factory.setPackagesToScan("com.opencrm.quote.quote", "com.opencrm.quote.lineitem", "com.opencrm.quote.ramp", "com.opencrm.common.valueobject");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.show_sql", "false"));
        factory.afterPropertiesSet(); emf = factory.getObject();
        tx = new TransactionTemplate(new JpaTransactionManager(emf));
        quotes = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf)).getRepository(QuoteRepository.class);
    }
    @AfterAll static void stop() { if (emf != null) emf.close(); if (postgres != null) postgres.stop(); }
    @BeforeEach void setup() {
        initial = tx.execute(status -> {
            Quote q = Quote.builder().name("Retry test").quoteNumber(UUID.randomUUID().toString())
                    .customerExternalId("synthetic-customer").createdAt(Instant.now()).status(QuoteStatus.DRAFT).build();
            return quotes.saveAndFlush(q);
        });
        routing = mock(ApprovalRoutingClient.class);
        submitter = new ApprovalService(mock(ApprovalRequestRepository.class), quotes, routing, false, mock(RampScheduleGuard.class));
        writer = new ApprovalProjectionWriter(quotes);
    }
    private RoutingApprovalDto canonical(String status) {
        return RoutingApprovalDto.builder().id(9001L).quoteId(initial.getId().toString()).approvalTier("DEAL_DESK").status(status).build();
    }
    private ApprovalSubmitRequest command() {
        return ApprovalSubmitRequest.builder().tier(ApprovalTier.DEAL_DESK).expectedVersion(initial.getVersion()).build();
    }
    private Quote stored() { return tx.execute(s -> quotes.findByIdAndDeletedFalse(initial.getId()).orElseThrow()); }

    @Test void routingCommitSurvivesQuoteTransactionRollbackAndRetryUsesSameId() {
        when(routing.submit(any())).thenAnswer(i -> receipts.computeIfAbsent(((RoutingApprovalSubmitRequest)i.getArgument(0)).getIdempotencyKey(), key -> canonical("PENDING")));
        when(routing.getById(9001L)).thenReturn(canonical("PENDING"));
        assertThatThrownBy(() -> tx.execute(status -> {
            submitter.submitForApproval(initial.getId(), command());
            quotes.flush(); // Force actual JPA/database write before failing the caller transaction.
            throw new IllegalStateException("forced failure after quote flush");
        })).hasMessage("forced failure after quote flush");
        assertThat(stored().getStatus()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(stored().getVersion()).isEqualTo(initial.getVersion());
        assertThat(receipts).hasSize(1);
        assertThat(tx.execute(s -> submitter.submitForApproval(initial.getId(), command())).getId()).isEqualTo(9001L);
        assertThat(stored().getStatus()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
        assertThat(receipts).hasSize(1);
    }

    @Test void decisionAfterCanonicalReadBeforeQuoteSaveIsRepairedByDurableSweep() {
        when(routing.submit(any())).thenReturn(canonical("PENDING"));
        AtomicReference<RoutingApprovalDto> authoritative = new AtomicReference<>(canonical("PENDING"));
        when(routing.getById(9001L)).thenAnswer(i -> {
            RoutingApprovalDto snapshot = authoritative.get();
            authoritative.set(canonical("APPROVED")); // Exact TOCTOU: decision commits after snapshot, before quote save.
            return snapshot;
        });
        tx.execute(s -> submitter.submitForApproval(initial.getId(), command()));
        Quote candidate = stored();
        assertThat(candidate.getStatus()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
        assertThat(authoritative.get().getStatus()).isEqualTo("APPROVED");
        ApprovalProjectionRepository candidates = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf))
                .getRepository(ApprovalProjectionRepository.class);
        assertThat(tx.<List<Quote>>execute(s -> candidates.pendingAfter(0, org.springframework.data.domain.PageRequest.of(0, 100))))
                .extracting(Quote::getId).contains(initial.getId());
        assertThat(tx.<ApprovalProjectionWriter.Outcome>execute(s -> writer.reconcile(candidate.getId(), candidate.getVersion(), 9001L, authoritative.get()))).isEqualTo(ApprovalProjectionWriter.Outcome.APPLIED);
        assertThat(stored().getStatus()).isEqualTo(QuoteStatus.APPROVED);
        assertThat(tx.<ApprovalProjectionWriter.Outcome>execute(s -> writer.reconcile(candidate.getId(), candidate.getVersion(), 9001L, authoritative.get()))).isEqualTo(ApprovalProjectionWriter.Outcome.ALREADY_FINAL);
    }

    @Test void changedQuoteVersionFencesAStaleReconciliationResult() {
        when(routing.submit(any())).thenReturn(canonical("PENDING"));
        when(routing.getById(9001L)).thenReturn(canonical("PENDING"));
        tx.execute(s -> submitter.submitForApproval(initial.getId(), command()));
        Quote stale = stored();
        tx.execute(s -> { Quote q = quotes.findById(initial.getId()).orElseThrow(); q.setStatus(QuoteStatus.REJECTED); quotes.saveAndFlush(q); return null; });
        assertThat(tx.<ApprovalProjectionWriter.Outcome>execute(s -> writer.reconcile(stale.getId(), stale.getVersion(), 9001L, canonical("APPROVED")))).isEqualTo(ApprovalProjectionWriter.Outcome.ALREADY_FINAL);
        assertThat(stored().getStatus()).isEqualTo(QuoteStatus.REJECTED);
    }
    @Test void concurrentReconciliationWorkersCannotBothCommitTheSameProjectionVersion() throws Exception {
        when(routing.submit(any())).thenReturn(canonical("PENDING"));
        when(routing.getById(9001L)).thenReturn(canonical("PENDING"));
        tx.execute(s -> submitter.submitForApproval(initial.getId(), command()));
        Quote candidate = stored();
        var bothRead = new java.util.concurrent.CountDownLatch(2);
        QuoteRepository racing = mock(QuoteRepository.class);
        when(racing.findByIdAndDeletedFalse(initial.getId())).thenAnswer(i -> {
            var managed = quotes.findByIdAndDeletedFalse(initial.getId());
            bothRead.countDown();
            if (!bothRead.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout");
            return managed;
        });
        when(racing.saveAndFlush(any())).thenAnswer(i -> quotes.saveAndFlush(i.getArgument(0)));
        var racingWriter = new ApprovalProjectionWriter(racing);
        var failures = new java.util.concurrent.ConcurrentLinkedQueue<RuntimeException>();
        java.util.concurrent.Callable<Boolean> work = () -> {
            try { return tx.execute(s -> racingWriter.reconcile(candidate.getId(), candidate.getVersion(), 9001L, canonical("APPROVED"))) == ApprovalProjectionWriter.Outcome.APPLIED; }
            catch (RuntimeException ex) { failures.add(ex); return false; }
        };
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = pool.submit(work); var b = pool.submit(work);
            assertThat(List.of(a.get(15, java.util.concurrent.TimeUnit.SECONDS), b.get(15, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(failures).hasSize(1);
        assertThat(failures.peek()).isInstanceOfAny(jakarta.persistence.OptimisticLockException.class,
                org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        assertThat(stored().getStatus()).isEqualTo(QuoteStatus.APPROVED);
        assertThat(stored().getVersion()).isEqualTo(candidate.getVersion() + 1);
    }
}
