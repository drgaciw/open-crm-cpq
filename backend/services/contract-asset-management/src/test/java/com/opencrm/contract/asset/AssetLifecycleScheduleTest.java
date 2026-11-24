package com.opencrm.contract.asset;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DRG-902 AC1. Enqueue is idempotent on dedupe_key. */
class AssetLifecycleScheduleTest {

    private final AssetLifecycleChangeRepository changes = mock(AssetLifecycleChangeRepository.class);
    private final TransactionOperations direct = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    };
    private final AssetLifecycleSchedule schedule = new AssetLifecycleSchedule(changes, direct);

    @Test
    void secondEnqueueWithTheSameDedupeKeyReturnsTheStoredRow() {
        AssetLifecycleChange stored = stored("subscription:line:CREATE");
        when(changes.findByDedupeKey("subscription:line:CREATE")).thenReturn(Optional.of(stored));

        assertThat(schedule.enqueue(command("subscription:line:CREATE"))).isSameAs(stored);
        verify(changes, never()).saveAndFlush(any());
        verify(changes, never()).nextSequence();
    }

    @Test
    void firstEnqueuePersistsAPendingRowWithTheNextSequence() {
        when(changes.findByDedupeKey("doc:1:line:AMEND")).thenReturn(Optional.empty());
        when(changes.nextSequence()).thenReturn(7L);
        when(changes.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AssetLifecycleChange saved = schedule.enqueue(command("doc:1:line:AMEND"));

        assertThat(saved.getStatus()).isEqualTo(AssetLifecycleChangeStatus.PENDING);
        assertThat(saved.getDedupeKey()).isEqualTo("doc:1:line:AMEND");
        assertThat(saved.getSequenceNo()).isEqualTo(7L);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getKind()).isEqualTo(AssetLifecycleChangeKind.AMEND);
        verify(changes).saveAndFlush(saved);
    }

    @Test
    void concurrentDuplicateInsertReturnsTheRowThatWonTheUniqueKey() {
        AssetLifecycleChange winner = stored("same-key");
        when(changes.findByDedupeKey("same-key")).thenReturn(Optional.empty(), Optional.of(winner));
        when(changes.nextSequence()).thenReturn(8L);
        when(changes.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate dedupe_key"));

        assertThat(schedule.enqueue(command("same-key"))).isSameAs(winner);
    }

    private static AssetLifecycleChange stored(String dedupeKey) {
        return AssetLifecycleChange.builder()
                .uuid("11111111-1111-4111-8111-111111111111")
                .dedupeKey(dedupeKey)
                .kind(AssetLifecycleChangeKind.CREATE)
                .subscriptionId(1L)
                .contractLineId("line")
                .accountId("acct")
                .effectiveDate(LocalDate.parse("2026-11-24"))
                .sequenceNo(1L)
                .sourceType(AssetLifecycleChange.Source.ACTIVATION)
                .payload("{}")
                .status(AssetLifecycleChangeStatus.PENDING)
                .build();
    }

    private static AssetLifecycleSchedule.Enqueue command(String dedupeKey) {
        return new AssetLifecycleSchedule.Enqueue(
                dedupeKey,
                AssetLifecycleChangeKind.AMEND,
                null,
                1L,
                "line",
                "acct",
                LocalDate.parse("2026-11-24"),
                AssetLifecycleChange.Source.DOCUMENT_APPLY,
                null,
                null,
                "corr",
                "{\"qty\":1}");
    }
}
