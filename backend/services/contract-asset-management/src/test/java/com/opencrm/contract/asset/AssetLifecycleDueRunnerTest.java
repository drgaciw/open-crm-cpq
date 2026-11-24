package com.opencrm.contract.asset;

import com.opencrm.contract.documents.apply.AssetLifecycleReadiness;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DRG-902 AC1 / C19 / AM09 / P04. Ordering, head-of-line, failure isolation, the hold, and the UTC date boundary.
 */
class AssetLifecycleDueRunnerTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-24");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T15:00:00Z"), ZoneOffset.UTC);

    private final AssetLifecycleReadiness readiness = mock(AssetLifecycleReadiness.class);
    private final AssetLifecycleChangeRepository changes = mock(AssetLifecycleChangeRepository.class);
    private final AssetLifecycleChangeApplier applier = mock(AssetLifecycleChangeApplier.class);
    private final AssetAuditOutbox audit = mock(AssetAuditOutbox.class);
    private AssetLifecycleDueRunner runner;

    @BeforeEach
    void setUp() {
        when(applier.kind()).thenReturn(AssetLifecycleChangeKind.AMEND);
        when(readiness.isReady()).thenReturn(true);
        when(changes.existsEarlierBlocking(anyLong(), any(), any(), anyLong())).thenReturn(false);
        runner = new AssetLifecycleDueRunner(readiness, changes, List.of(applier), audit, CLOCK, 50);
    }

    @Test
    void appliesDueRowsInEffectiveDateThenSequenceOrder() {
        AssetLifecycleChange earlier = row("line-a", TODAY.minusDays(1), 9L);
        AssetLifecycleChange later = row("line-b", TODAY, 2L);
        when(changes.lockDue(TODAY, 50)).thenReturn(List.of(earlier, later));

        assertThat(runner.runDue()).isEqualTo(2);

        InOrder order = inOrder(applier);
        order.verify(applier).apply(earlier);
        order.verify(applier).apply(later);
        assertThat(earlier.getStatus()).isEqualTo(AssetLifecycleChangeStatus.APPLIED);
        assertThat(later.getStatus()).isEqualTo(AssetLifecycleChangeStatus.APPLIED);
        assertThat(earlier.getAppliedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void skipsALineWhileAnEarlierPendingOrFailedRowExists() {
        AssetLifecycleChange blocked = row("line", TODAY, 2L);
        AssetLifecycleChange other = row("other", TODAY, 1L);
        when(changes.lockDue(TODAY, 50)).thenReturn(List.of(blocked, other));
        when(changes.existsEarlierBlocking(1L, "line", TODAY, 2L)).thenReturn(true);

        assertThat(runner.runDue()).isEqualTo(1);

        assertThat(blocked.getStatus()).isEqualTo(AssetLifecycleChangeStatus.PENDING);
        assertThat(blocked.getAttempts()).isZero();
        assertThat(other.getStatus()).isEqualTo(AssetLifecycleChangeStatus.APPLIED);
        verify(applier, never()).apply(blocked);
        verifyNoInteractions(audit);
    }

    @Test
    void failureMarksThatLineFailedAndLetsTheOtherLineContinue() {
        AssetLifecycleChange first = row("line", TODAY.minusDays(1), 1L);
        AssetLifecycleChange second = row("line", TODAY, 2L);
        AssetLifecycleChange other = row("other", TODAY, 3L);
        when(changes.lockDue(TODAY, 50)).thenReturn(List.of(first, second, other));
        doThrow(new IllegalStateException("boom")).when(applier).apply(first);

        assertThat(runner.runDue()).isEqualTo(1);

        assertThat(first.getStatus()).isEqualTo(AssetLifecycleChangeStatus.FAILED);
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).isEqualTo("boom");
        assertThat(second.getStatus()).isEqualTo(AssetLifecycleChangeStatus.PENDING);
        assertThat(second.getAttempts()).isZero();
        assertThat(other.getStatus()).isEqualTo(AssetLifecycleChangeStatus.APPLIED);
        verify(applier, never()).apply(second);
        verify(audit).stage(any(Asset.class), any(), eq(AssetLifecycleDueRunner.AUDIT_FAILED));
    }

    @Test
    void doesNothingWhenTheLifecycleHoldIsOn() {
        when(readiness.isReady()).thenReturn(false);

        assertThat(runner.runDue()).isZero();

        verifyNoInteractions(changes, applier, audit);
    }

    @Test
    void effectiveDateEqualToTodayIsDueAndTheNextUtcDayIsNot() {
        AssetLifecycleChange yesterday = row("yesterday", TODAY.minusDays(1), 1L);
        AssetLifecycleChange today = row("today", TODAY, 2L);
        AssetLifecycleChange tomorrow = row("tomorrow", TODAY.plusDays(1), 3L);
        when(changes.lockDue(TODAY, 50)).thenReturn(List.of(yesterday, today, tomorrow));

        assertThat(runner.runDue()).isEqualTo(2);

        assertThat(yesterday.getStatus()).isEqualTo(AssetLifecycleChangeStatus.APPLIED);
        assertThat(today.getStatus()).isEqualTo(AssetLifecycleChangeStatus.APPLIED);
        assertThat(tomorrow.getStatus()).isEqualTo(AssetLifecycleChangeStatus.PENDING);
        verify(changes).lockDue(TODAY, 50);
        verify(applier, never()).apply(tomorrow);
    }

    @Test
    void jobDoesNothingAndLeavesTheGaugeAtZeroWhileTheHoldIsOn() {
        when(readiness.isReady()).thenReturn(false);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        AssetLifecycleDueRunner idle = mock(AssetLifecycleDueRunner.class);
        AssetLifecycleDueJob job = new AssetLifecycleDueJob(readiness, idle, changes, CLOCK, meters);

        job.tick();

        verify(idle, never()).runDue();
        verify(changes, never()).findOldestDueDate(any());
        assertThat(meters.get(AssetLifecycleDueJob.OVERDUE_GAUGE).gauge().value()).isZero();
    }

    @Test
    void jobRecordsOverdueSecondsAndRunsWhenReady() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        AssetLifecycleDueRunner idle = mock(AssetLifecycleDueRunner.class);
        when(changes.findOldestDueDate(TODAY)).thenReturn(TODAY.minusDays(1));
        AssetLifecycleDueJob job = new AssetLifecycleDueJob(readiness, idle, changes, CLOCK, meters);

        job.tick();

        verify(idle).runDue();
        assertThat(meters.get(AssetLifecycleDueJob.OVERDUE_GAUGE).gauge().value()).isEqualTo(39d * 60d * 60d);
    }

    private static AssetLifecycleChange row(String line, LocalDate effectiveDate, long sequenceNo) {
        return AssetLifecycleChange.builder()
                .uuid("row-" + line + "-" + sequenceNo)
                .dedupeKey(line + ":" + sequenceNo)
                .kind(AssetLifecycleChangeKind.AMEND)
                .subscriptionId(1L)
                .contractLineId(line)
                .accountId("acct")
                .effectiveDate(effectiveDate)
                .sequenceNo(sequenceNo)
                .sourceType(AssetLifecycleChange.Source.DOCUMENT_APPLY)
                .payload("{}")
                .status(AssetLifecycleChangeStatus.PENDING)
                .attempts(0)
                .build();
    }
}
