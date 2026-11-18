package com.opencrm.approval.service;

import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.dto.*;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** Real PostgreSQL receipt/concurrency boundary; canonical writer is a SQL-backed test double. */
@Tag("integration")
class ApprovalSubmissionReceiptIntegrationTest {
    private static PostgreSQLContainer<?> postgres;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private IdempotentApprovalSubmission submission;
    private ApprovalSubmitRequest command;

    @BeforeAll static void database() {
        if (System.getProperty("receipt.test.jdbc") == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine"); postgres.start();
        }
    }
    @AfterAll static void stopDatabase() { if (postgres != null) postgres.stop(); }
    @BeforeEach void setup() throws Exception {
        DriverManagerDataSource data = postgres == null
                ? new DriverManagerDataSource(System.getProperty("receipt.test.jdbc"), "receipt_test", "")
                : new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(data); tx = new TransactionTemplate(new DataSourceTransactionManager(data));
        jdbc.execute("DROP TABLE IF EXISTS approval_submit_receipts");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V1_11__approval_submit_receipts.sql")));
        jdbc.execute("DROP TABLE IF EXISTS canonical_test_approvals");
        jdbc.execute("CREATE TABLE canonical_test_approvals (id BIGSERIAL PRIMARY KEY)");
        ApprovalService approvals = mock(ApprovalService.class);
        when(approvals.submit(any())).thenAnswer(i -> {
            Long id = jdbc.queryForObject("INSERT INTO canonical_test_approvals DEFAULT VALUES RETURNING id", Long.class);
            return ApprovalRequestDto.builder().id(id).quoteId("42").status(ApprovalStatus.PENDING).approvalTier("DEAL_DESK").build();
        });
        CurrentActor actor = mock(CurrentActor.class); when(actor.id()).thenReturn("seller");
        submission = new IdempotentApprovalSubmission(jdbc, approvals, actor);
        command = ApprovalSubmitRequest.builder().quoteId("42").tier(QuoteApprovalTier.DEAL_DESK).build();
    }
    private ApprovalRequestDto submit() { return tx.execute(status -> submission.submit("quote:42:v7", command)); }

    @Test void concurrentReplaysCommitExactlyOneCanonicalRequest() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
            Callable<Long> call = () -> { ready.countDown(); go.await(); return submit().getId(); };
            var a = workers.submit(call); var b = workers.submit(call);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM canonical_test_approvals", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_submit_receipts WHERE response_json IS NOT NULL", Integer.class)).isEqualTo(1);
        }
    }
    @Test void quoteFailureAfterRoutingCommitDoesNotLoseReceiptAndReplayRejectsChangedPayload() {
        Long id = submit().getId(); // Remote routing transaction committed before caller persistence fails.
        assertThatThrownBy(() -> tx.execute(status -> { throw new IllegalStateException("quote save failed"); })).hasMessage("quote save failed");
        assertThat(submit().getId()).isEqualTo(id);
        command.setTier(QuoteApprovalTier.CFO);
        assertThatThrownBy(this::submit).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM canonical_test_approvals", Integer.class)).isEqualTo(1);
    }
    @Test void rollbackDoesNotLeaveAnIncompleteReceipt() {
        assertThatThrownBy(() -> tx.execute(status -> {
            submission.submit("quote:42:v7", command); throw new IllegalStateException("before commit");
        })).hasMessage("before commit");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_submit_receipts", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM canonical_test_approvals", Integer.class)).isZero();
        assertThat(submit().getId()).isNotNull();
    }
}
