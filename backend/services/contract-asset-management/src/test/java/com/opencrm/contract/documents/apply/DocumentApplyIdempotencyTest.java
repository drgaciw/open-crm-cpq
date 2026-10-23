package com.opencrm.contract.documents.apply;

import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.DocumentApplyLedgerEntry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AC-S4-2. DoD v0.3 Core 3, v0.2 add 4, v0.2 add 5, v0.3 add 2. */
@Tag("unit")
class DocumentApplyIdempotencyTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Test
    void repeatIsANoOp() {
        ApplyFixture fixture = executedAdd();
        ApplyResult first = fixture.apply();
        int events = fixture.events.size();
        int periods = fixture.periods.size();

        ApplyResult second = fixture.apply();

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(fixture.ledger).hasSize(1);
        assertThat(fixture.periods).hasSize(periods);
        assertThat(fixture.events).hasSize(events);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_REFUSED)).isZero();
    }

    @Test
    void digestConflictIsRejectedAndAudited() {
        ApplyFixture fixture = executedAdd();
        fixture.ledger.put(fixture.document.getId() + ":1", DocumentApplyLedgerEntry.builder()
                .documentId(fixture.document.getId())
                .unitNo(1)
                .tenantId(fixture.tenantId)
                .applyKeyDigest("ab".repeat(32))
                .createdBy("earlier")
                .build());

        assertThatThrownBy(fixture::apply)
                .isInstanceOf(DocumentApplyException.class)
                .extracting(ex -> ((DocumentApplyException) ex).getErrorCode())
                .isEqualTo("DIGEST_CONFLICT");

        assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.EXECUTED);
        assertThat(fixture.ledger).hasSize(1);
        assertThat(fixture.ledger.values().iterator().next().getApplyKeyDigest()).isEqualTo("ab".repeat(32));
        assertThat(fixture.periods).isEmpty();
        assertThat(fixture.events).hasSize(1);
        var audit = fixture.events.get(0);
        assertThat(audit.eventType()).isEqualTo(DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED);
        assertThat(audit.reasonCode()).isEqualTo("DIGEST_CONFLICT");
        assertThat(audit.unitNo()).isEqualTo(1);
        assertThat(audit.documentId()).isEqualTo(fixture.document.getId());
        assertThat(audit.payloadDigest()).matches("[0-9a-f]{64}");
        assertThat(audit.actorId()).isEqualTo("quote-service");
        assertThat(audit.actorRole()).isEqualTo("ROLE_INTERNAL_INVOKE");
    }

    @Test
    void concurrentApplySerializesOnTheAdvisoryLock() throws Exception {
        ApplyFixture fixture = executedAdd();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        try {
            List<Future<ApplyResult>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    ready.await();
                    return fixture.apply();
                }));
            }
            ApplyResult first = results.get(0).get();
            ApplyResult second = results.get(1).get();
            assertThat(List.of(first.replayed(), second.replayed())).contains(false);
            assertThat(fixture.ledger).hasSize(1);
            assertThat(fixture.periods).hasSize(1);
            assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        } finally {
            pool.shutdownNow();
        }
    }

    private static ApplyFixture executedAdd() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 3L, START, END);
        fixture.unit(1, subscription, 3L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        return fixture;
    }
}
