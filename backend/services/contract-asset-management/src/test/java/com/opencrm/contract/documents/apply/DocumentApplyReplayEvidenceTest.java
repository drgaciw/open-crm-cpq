package com.opencrm.contract.documents.apply;

import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class DocumentApplyReplayEvidenceTest {
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Test
    void ruleBlockedReplayReturnsCommittedReasonWithoutRepeatingWork() {
        ApplyFixture f = new ApplyFixture();
        var a = f.subscription(1, 1, START, END);
        var b = f.subscription(2, 2, START, END);
        f.unit(1, a, 1);
        f.unit(2, b, 1);
        f.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        f.line(2, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        var first = f.apply();
        int eventCount = f.events.size();
        var replay = f.apply();
        assertThat(first.status()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.unitOutcomes()).isEqualTo(first.unitOutcomes());
        assertThat(replay.unitOutcomes().get(1).reason()).isEqualTo("V3");
        assertThat(f.events).hasSize(eventCount);
        assertThat(f.periods).hasSize(1);
    }

    @Test
    void observedPinIsReplayIdentityIncludingAbsentVersusEmpty() {
        ApplyFixture f = new ApplyFixture();
        var a = f.subscription(1, 1, START, END);
        f.unit(1, a, 1);
        f.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        var initial = f.command();
        f.orchestrator.apply(initial);
        var emptyPin = new ApplyCommand(initial.amendmentId(), initial.tenantId(), initial.callerId(),
                initial.authorities(), initial.correlationId(), Map.of(1, ""));
        assertThatThrownBy(() -> f.orchestrator.apply(emptyPin)).isInstanceOfSatisfying(DocumentApplyException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT"));
        assertThat(f.periods).hasSize(1);
    }

    @Test
    void changedPinCannotReplayAnEarlierV9Block() {
        ApplyFixture f = new ApplyFixture();
        var a = f.subscription(1, 1, START, END);
        f.unit(1, a, 1);
        f.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        var initial = f.command();
        var wrong = new ApplyCommand(initial.amendmentId(), initial.tenantId(), initial.callerId(),
                initial.authorities(), initial.correlationId(), Map.of(1, "pb-other"));
        var first = f.orchestrator.apply(wrong);
        assertThat(first.unitOutcomes().getFirst().reason()).isEqualTo("V9");
        assertThat(f.orchestrator.apply(wrong).unitOutcomes()).isEqualTo(first.unitOutcomes());
        var changed = new ApplyCommand(initial.amendmentId(), initial.tenantId(), initial.callerId(),
                initial.authorities(), initial.correlationId(), Map.of(1, "pb-1"));
        assertThatThrownBy(() -> f.orchestrator.apply(changed)).isInstanceOfSatisfying(DocumentApplyException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT"));
        assertThat(f.periods).isEmpty();
    }
    @Test
    void terminalLegacyAttemptWithoutPinEvidenceFailsClosed() {
        ApplyFixture f = new ApplyFixture();
        var a = f.subscription(1, 1, START, END);
        var unit = f.unit(1, a, 1);
        f.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        f.apply();
        unit.setAttemptPinFingerprint(null);
        assertThatThrownBy(f::apply).isInstanceOfSatisfying(DocumentApplyException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT"));
        assertThat(f.periods).hasSize(1);
    }

    @Test
    void blockedUnitWithMatchingLedgerFailsClosed() {
        ApplyFixture f = new ApplyFixture();
        var a = f.subscription(1, 2, START, END);
        var unit = f.unit(1, a, 1);
        f.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        f.apply();
        f.ledger.put(f.document.getId() + ":1", com.opencrm.contract.domain.documents.DocumentApplyLedgerEntry.builder()
                .documentId(f.document.getId()).tenantId(f.tenantId).unitNo(1).applyKeyDigest(unit.getApplyKeyDigest()).createdBy("bad-state").build());
        assertThatThrownBy(f::apply).isInstanceOfSatisfying(DocumentApplyException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT"));
        assertThat(f.periods).isEmpty();
    }

    @Test
    void recoveredPartialOutcomeCountsAlertOnlyOnce() {
        ApplyFixture f = new ApplyFixture();
        var a = f.subscription(1, 1, START, END);
        var b = f.subscription(2, 2, START, END);
        f.unit(1, a, 1);
        f.unit(2, b, 1);
        f.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        f.line(2, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        f.apply();
        f.document.setStatus(AmendmentDocumentStatus.EXECUTED);
        double before = f.meters.counter(DocumentApplyOrchestrator.ALERT_METRIC, "status", "PARTIALLY_APPLIED").count();
        assertThat(f.apply().status()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(f.apply().replayed()).isTrue();
        assertThat(f.meters.counter(DocumentApplyOrchestrator.ALERT_METRIC, "status", "PARTIALLY_APPLIED").count()).isEqualTo(before + 1);
    }

    @Test
    void rechecksPendingLedgerUnderUnitLockBeforeApplyingSiblings() {
        ApplyFixture f = new ApplyFixture();
        var a = f.subscription(1, 1, START, END);
        var b = f.subscription(2, 1, START, END);
        var first = f.unit(1, a, 1);
        var sibling = f.unit(2, b, 1);
        f.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        f.line(2, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        String digest = DocumentApplyDigest.unitDigest(f.document.getId(), first, f.lines.stream().filter(line -> line.unitNo() == 1).toList());
        f.afterDocumentLock = call -> {
            if (call == 2) f.ledger.put(f.document.getId() + ":1", com.opencrm.contract.domain.documents.DocumentApplyLedgerEntry.builder()
                    .documentId(f.document.getId()).tenantId(f.tenantId).unitNo(1).applyKeyDigest(digest).createdBy("racer").build());
        };
        assertThatThrownBy(f::apply).isInstanceOfSatisfying(DocumentApplyException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT"));
        assertThat(first.getStatus()).isEqualTo(com.opencrm.contract.domain.documents.ApplicationUnitStatus.PENDING);
        assertThat(sibling.getStatus()).isEqualTo(com.opencrm.contract.domain.documents.ApplicationUnitStatus.PENDING);
        assertThat(f.periods).isEmpty();
    }

    @Test
    void rechecksTenantBeforeRecordingDigestConflictBlock() {
        ApplyFixture f = new ApplyFixture();
        var a = f.subscription(1, 1, START, END);
        var b = f.subscription(2, 1, START, END);
        f.unit(1, a, 1);
        var conflicting = f.unit(2, b, 1);
        f.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        f.line(2, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        f.ledger.put(f.document.getId() + ":2", com.opencrm.contract.domain.documents.DocumentApplyLedgerEntry.builder()
                .documentId(f.document.getId()).tenantId(f.tenantId).unitNo(2).applyKeyDigest("ab".repeat(32)).createdBy("old").build());
        f.afterDocumentLock = call -> { if (call == 4) conflicting.setTenantId(java.util.UUID.randomUUID()); };
        assertThatThrownBy(f::apply).isInstanceOfSatisfying(DocumentApplyException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT"));
        assertThat(conflicting.getStatus()).isEqualTo(com.opencrm.contract.domain.documents.ApplicationUnitStatus.PENDING);
        assertThat(conflicting.getBlockReason()).isNull();
        assertThat(f.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED)).isZero();
    }

    @Test
    void changedPinOnCommittedUnitRejectsBeforeEarlierPendingSibling() {
        ApplyFixture f = new ApplyFixture();
        var a = f.subscription(1, 1, START, END);
        var b = f.subscription(2, 1, START, END);
        var first = f.unit(1, a, 1);
        var sibling = f.unit(2, b, 1);
        f.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        f.line(2, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        var initial = f.command();
        var original = new ApplyCommand(initial.amendmentId(), initial.tenantId(), initial.callerId(), initial.authorities(), initial.correlationId(), Map.of(1, "pb-1", 2, "pb-1"));
        f.orchestrator.apply(original);
        sibling.setStatus(com.opencrm.contract.domain.documents.ApplicationUnitStatus.PENDING);
        sibling.setApplyKeyDigest(null);
        sibling.setAttemptPinFingerprint(null);
        sibling.setAttemptRevisionNo(null);
        f.ledger.remove(f.document.getId() + ":2");
        f.periods.removeIf(period -> period.getSubscriptionId().equals(2L));
        f.document.setStatus(AmendmentDocumentStatus.EXECUTED);
        first.setDependencyOrder(2);
        sibling.setDependencyOrder(1);
        var changed = new ApplyCommand(initial.amendmentId(), initial.tenantId(), initial.callerId(), initial.authorities(), initial.correlationId(), Map.of(1, "pb-other", 2, "pb-1"));
        assertThatThrownBy(() -> f.orchestrator.apply(changed)).isInstanceOfSatisfying(DocumentApplyException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT"));
        assertThat(sibling.getStatus()).isEqualTo(com.opencrm.contract.domain.documents.ApplicationUnitStatus.PENDING);
        assertThat(f.periods).hasSize(1);
    }

    @Test
    void legacyBlockedUnitWithoutReasonIsRejected() {
        ApplyFixture f = new ApplyFixture();
        var a = f.subscription(1, 2, START, END);
        var unit = f.unit(1, a, 1);
        f.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        f.apply();
        unit.setBlockReason(null);
        assertThatThrownBy(f::apply).isInstanceOfSatisfying(DocumentApplyException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT"));
        assertThat(f.periods).isEmpty();
    }

    @Test
    void aNewRevisionCannotReuseTerminalUnitEvidence() {
        ApplyFixture f = new ApplyFixture();
        var a = f.subscription(1, 1, START, END);
        f.unit(1, a, 1);
        f.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        f.apply();
        f.document.setCurrentRevisionNo(2);
        f.document.setExecutedRevisionNo(2);
        assertThatThrownBy(f::apply).isInstanceOfSatisfying(DocumentApplyException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT"));
        assertThat(f.periods).hasSize(1);
    }

    @Test
    void neverFinalizesAnOutcomeIfAUnitIsPendingUnderTheFinalLock() {
        ApplyFixture f = new ApplyFixture();
        var first = f.subscription(1, 1, START, END);
        var later = f.subscription(2, 1, START, END);
        f.unit(1, first, 1);
        f.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        f.afterDocumentLock = call -> {
            if (call == 3) {
                f.unit(2, later, 1);
                f.line(2, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
            }
        };
        assertThatThrownBy(f::apply).isInstanceOfSatisfying(DocumentApplyException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT"));
        assertThat(f.document.getStatus()).isEqualTo(AmendmentDocumentStatus.EXECUTED);
        assertThat(f.units.get(1).getStatus()).isEqualTo(com.opencrm.contract.domain.documents.ApplicationUnitStatus.PENDING);
        assertThat(f.eventsOfType(DocumentApplyOrchestrator.EVENT_DOCUMENT_PARTIAL)).isZero();
        assertThat(f.eventsOfType(DocumentApplyOrchestrator.EVENT_DOCUMENT_APPLIED)).isZero();
    }

    @Test
    void concurrentAttemptCanFinishWhileFirstWaitsAfterItsFirstUnit() throws Exception {
        ApplyFixture f = new ApplyFixture();
        var a = f.subscription(1, 1, START, END);
        var b = f.subscription(2, 1, START, END);
        f.unit(1, a, 1);
        f.unit(2, b, 1);
        f.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        f.line(2, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        var firstCommitted = new java.util.concurrent.CountDownLatch(1);
        var resume = new java.util.concurrent.CountDownLatch(1);
        f.afterDocumentUnlock = call -> {
            if (call == 2) {
                firstCommitted.countDown();
                try {
                    if (!resume.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("second apply timed out");
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            }
        };
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(f::apply);
            assertThat(firstCommitted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(f::apply);
            assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS).status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
            resume.countDown();
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS).status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
            assertThat(f.units).allMatch(unit -> unit.getStatus() == com.opencrm.contract.domain.documents.ApplicationUnitStatus.APPLIED);
            assertThat(f.ledger).hasSize(2);
            assertThat(f.periods).hasSize(2);
            assertThat(f.eventsOfType(DocumentApplyOrchestrator.EVENT_DOCUMENT_APPLIED)).isEqualTo(1);
        } finally { resume.countDown(); pool.shutdownNow(); }
    }

}
