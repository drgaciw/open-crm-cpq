package com.opencrm.quote.approval;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.*;
import com.opencrm.quote.quote.*;
import com.opencrm.quote.ramp.RampScheduleGuard;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
class ApprovalSubmissionRecoveryTest {
    private final QuoteRepository quotes = mock(QuoteRepository.class);
    private final ApprovalRoutingClient routing = mock(ApprovalRoutingClient.class);
    private final ApprovalRequestRepository legacy = mock(ApprovalRequestRepository.class);
    private final ApprovalService service = new ApprovalService(legacy, quotes, routing, false, mock(RampScheduleGuard.class));
    private Quote quote(long version) {
        Quote q = Quote.builder().quoteNumber("Q-42").status(QuoteStatus.DRAFT).build();
        q.setId(42L); q.setUuid("quote-42"); q.setVersion(version); return q;
    }
    private ApprovalSubmitRequest command(long version) {
        return ApprovalSubmitRequest.builder().tier(ApprovalTier.DEAL_DESK).expectedVersion(version).build();
    }
    private RoutingApprovalDto result() {
        return RoutingApprovalDto.builder().id(9001L).quoteId("42").status("PENDING").approvalTier("DEAL_DESK").build();
    }
    @Test void routingCommitsThenQuoteSaveFailsAndRetryRestoresProjectionUsingSameCanonicalId() {
        when(routing.getById(9001L)).thenReturn(result());
        Map<String, RoutingApprovalDto> receipts = new HashMap<>();
        when(routing.submit(any())).thenAnswer(i -> receipts.computeIfAbsent(((RoutingApprovalSubmitRequest)i.getArgument(0)).getIdempotencyKey(), key -> result()));
        Quote first = quote(7), retry = quote(7);
        when(quotes.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(first), Optional.of(retry));
        AtomicInteger saves = new AtomicInteger();
        when(quotes.save(any())).thenAnswer(i -> {
            if (saves.incrementAndGet() == 1) throw new IllegalStateException("forced quote persistence failure");
            return i.getArgument(0);
        });
        assertThatThrownBy(() -> service.submitForApproval(42L, command(7))).hasMessage("forced quote persistence failure");
        assertThat(receipts).hasSize(1);
        assertThat(service.submitForApproval(42L, command(7)).getId()).isEqualTo(9001L);
        assertThat(receipts).hasSize(1);
        assertThat(retry.getStatus()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
        assertThat(retry.getApprovalSubmissionId()).isEqualTo(9001L);
        assertThat(retry.getApprovalSubmissionVersion()).isEqualTo(7L);
        verifyNoInteractions(legacy);
    }
    @Test void replayAfterSuccessfulProjectionDoesNotWriteOrCreateAnotherApproval() {
        Quote q = quote(8); q.setStatus(QuoteStatus.PENDING_APPROVAL); q.setApprovalTier("DEAL_DESK");
        q.setApprovalSubmissionId(9001L); q.setApprovalSubmissionVersion(7L);
        when(quotes.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(q));
        when(routing.getById(9001L)).thenReturn(result());
        assertThat(service.submitForApproval(42L, command(7)).getId()).isEqualTo(9001L);
        verify(routing, never()).submit(any()); verify(quotes, never()).save(any());
    }
    @Test void staleCommandIsRefusedBeforeRouting() {
        when(quotes.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote(8)));
        assertThatThrownBy(() -> service.submitForApproval(42L, command(7))).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException)e).getErrorCode()).isEqualTo("STALE_APPROVAL_SUBMISSION");
        verifyNoInteractions(routing); verify(quotes, never()).save(any());
    }
    @Test void terminalCanonicalStateCannotRestorePendingFromFrozenReceipt() {
        when(quotes.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote(7)));
        when(routing.submit(any())).thenReturn(result());
        when(routing.getById(9001L)).thenReturn(RoutingApprovalDto.builder().id(9001L).quoteId("42")
                .approvalTier("DEAL_DESK").status("APPROVED").build());
        assertThatThrownBy(() -> service.submitForApproval(42L, command(7))).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException)e).getErrorCode()).isEqualTo("APPROVAL_SUBMISSION_STATE_CHANGED");
        verify(quotes, never()).save(any());
    }
    @Test void missingVersionCannotGenerateAnotherKeyAfterConcurrentEdit() {
        when(quotes.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote(8)));
        assertThatThrownBy(() -> service.submitForApproval(42L,
                ApprovalSubmitRequest.builder().tier(ApprovalTier.DEAL_DESK).build()))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getErrorCode())
                .isEqualTo("APPROVAL_VERSION_REQUIRED");
        verifyNoInteractions(routing);
    }
    @Test void failedSaveThenConcurrentEditRejectsOriginalCommandWithoutAnotherRoutingCall() {
        when(quotes.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote(7)), Optional.of(quote(8)));
        when(routing.submit(any())).thenReturn(result());
        when(routing.getById(9001L)).thenReturn(result());
        when(quotes.save(any())).thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Quote.class, 42L));
        assertThatThrownBy(() -> service.submitForApproval(42L, command(7)))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        assertThatThrownBy(() -> service.submitForApproval(42L, command(7))).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException)e).getErrorCode()).isEqualTo("STALE_APPROVAL_SUBMISSION");
        verify(routing, times(1)).submit(any());
    }
}
