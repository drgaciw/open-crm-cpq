package com.opencrm.quote.approval.recovery;

import com.opencrm.quote.client.*;
import com.opencrm.quote.quote.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ApprovalProjectionRecoveryTest {
    private final QuoteRepository quotes = mock(QuoteRepository.class);
    private final ApprovalProjectionWriter writer = new ApprovalProjectionWriter(quotes);
    private Quote pending() {
        Quote q = Quote.builder().status(QuoteStatus.PENDING_APPROVAL).approvalState(ApprovalState.PENDING)
                .approvalTier("DEAL_DESK").approvalSubmissionId(90L).approvalSubmissionVersion(7L).build();
        q.setId(42L); q.setVersion(8L); return q;
    }
    private RoutingApprovalDto canonical(String status) {
        return RoutingApprovalDto.builder().id(90L).quoteId("42").status(status).approvalTier("DEAL_DESK").build();
    }
    @Test void appliesTerminalDecisionWithVersionAndCanonicalBinding() {
        Quote q = pending(); when(quotes.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(q));
        assertThat(writer.reconcile(42L, 8L, 90L, canonical("REJECTED"))).isEqualTo(ApprovalProjectionWriter.Outcome.APPLIED);
        assertThat(q.getStatus()).isEqualTo(QuoteStatus.REJECTED);
        verify(quotes).saveAndFlush(q);
    }
    @Test void pendingCanonicalNeedsNoWriteAndNoTransactionRead() {
        assertThat(writer.reconcile(42L, 8L, 90L, canonical("PENDING"))).isNotEqualTo(ApprovalProjectionWriter.Outcome.APPLIED);
        verifyNoInteractions(quotes);
    }
    @Test void changedBindingOrVersionNeverOverwritesQuote() {
        Quote q = pending(); q.setVersion(9L);
        when(quotes.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(q));
        assertThat(writer.reconcile(42L, 8L, 90L, canonical("APPROVED"))).isNotEqualTo(ApprovalProjectionWriter.Outcome.APPLIED);
        assertThat(writer.reconcile(42L, 9L, 90L, canonical("APPROVED"))).isNotEqualTo(ApprovalProjectionWriter.Outcome.APPLIED);
        q.setVersion(8L); q.setApprovalSubmissionId(91L);
        assertThat(writer.reconcile(42L, 8L, 90L, canonical("APPROVED"))).isNotEqualTo(ApprovalProjectionWriter.Outcome.APPLIED);
        verify(quotes, never()).saveAndFlush(any());
    }
    @Test void absentDeletedOrRampQuoteIsNotReconciled() {
        when(quotes.findByIdAndDeletedFalse(42L)).thenReturn(Optional.empty());
        assertThat(writer.reconcile(42L, 8L, 90L, canonical("APPROVED"))).isNotEqualTo(ApprovalProjectionWriter.Outcome.APPLIED);
        Quote q = pending(); q.setRampScheduleStale(true);
        when(quotes.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(q));
        assertThat(writer.reconcile(42L, 8L, 90L, canonical("APPROVED"))).isNotEqualTo(ApprovalProjectionWriter.Outcome.APPLIED);
        verify(quotes, never()).saveAndFlush(any());
    }
    @Test void mismatchedCanonicalDoesNotReadOrWriteQuote() {
        assertThat(writer.reconcile(42L, 8L, 91L, canonical("APPROVED"))).isNotEqualTo(ApprovalProjectionWriter.Outcome.APPLIED);
        assertThat(writer.reconcile(43L, 8L, 90L, canonical("APPROVED"))).isNotEqualTo(ApprovalProjectionWriter.Outcome.APPLIED);
        assertThat(writer.reconcile(42L, 8L, 90L, null)).isNotEqualTo(ApprovalProjectionWriter.Outcome.APPLIED);
        verifyNoInteractions(quotes);
    }
    @Test void transportFailureDoesNotStarveNextBatchAndRestartRepeatsSweep() {
        ApprovalProjectionRepository candidates = mock(ApprovalProjectionRepository.class);
        ApprovalRoutingClient routing = mock(ApprovalRoutingClient.class);
        ApprovalProjectionWriter writes = mock(ApprovalProjectionWriter.class);
        when(candidates.pendingAfter(eq(0L), any())).thenReturn(List.of(pending()));
        when(candidates.pendingAfter(eq(42L), any())).thenReturn(List.of());
        when(routing.getById(90L)).thenThrow(new IllegalStateException("offline"));
        ApprovalProjectionReconciler sweep = new ApprovalProjectionReconciler(candidates, routing, writes, new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), Runnable::run);
        sweep.reconcile(); sweep.reconcile(); sweep.reconcile();
        verify(candidates, times(2)).pendingAfter(eq(0L), any());
        verify(candidates).pendingAfter(eq(42L), any());
        verifyNoInteractions(writes);
    }
    @Test void schedulerTickEnqueuesAtMostOneWorkerWithoutDoingDatabaseOrNetworkIo() {
        var candidates = mock(ApprovalProjectionRepository.class);
        var routing = mock(ApprovalRoutingClient.class);
        var writes = mock(ApprovalProjectionWriter.class);
        List<Runnable> tasks = new ArrayList<>();
        var sweep = new ApprovalProjectionReconciler(candidates, routing, writes,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), tasks::add);
        sweep.tick(); sweep.tick();
        assertThat(tasks).hasSize(1);
        verifyNoInteractions(candidates, routing, writes);
        when(candidates.pendingAfter(eq(0L), any())).thenReturn(List.of());
        tasks.getFirst().run();
        sweep.tick();
        assertThat(tasks).hasSize(2);
    }
    @Test void blockedTerminalProjectionHasDistinctObservableOutcome() {
        var candidates = mock(ApprovalProjectionRepository.class);
        var routing = mock(ApprovalRoutingClient.class);
        var writes = mock(ApprovalProjectionWriter.class);
        var meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        when(candidates.pendingAfter(eq(0L), any())).thenReturn(List.of(pending()));
        var canonical = canonical("APPROVED");
        when(routing.getById(90L)).thenReturn(canonical);
        when(writes.reconcile(42L, 8L, 90L, canonical)).thenReturn(ApprovalProjectionWriter.Outcome.STALE_BINDING);
        new ApprovalProjectionReconciler(candidates, routing, writes, meters, Runnable::run).reconcile();
        assertThat(meters.get("approval.projection.reconcile").tag("outcome", "STALE_BINDING").counter().count()).isEqualTo(1);
    }
}
