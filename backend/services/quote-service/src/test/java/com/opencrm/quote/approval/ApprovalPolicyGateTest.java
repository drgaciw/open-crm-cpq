package com.opencrm.quote.approval;

import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.client.RoutingApprovalDto;
import com.opencrm.quote.client.RoutingApprovalSubmitRequest;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import com.opencrm.quote.ramp.RampScheduleGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("submit uses EvaluateQuote and does not write a blocked quote")
class ApprovalPolicyGateTest {

    @Mock
    private ApprovalRequestRepository approvalRepository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private ApprovalRoutingClient approvalRoutingClient;
    @Mock
    private RampScheduleGuard rampScheduleGuard;
    @Mock
    private QuotePolicyEvaluation quotePolicyEvaluation;

    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(
                approvalRepository, quoteRepository, approvalRoutingClient, true, rampScheduleGuard, quotePolicyEvaluation);
    }

    @Test
    void blockedVerdictDoesNotWriteAnApproval() {
        Quote quote = draft();
        when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote));
        when(quotePolicyEvaluation.evaluate(quote)).thenReturn(new QuotePolicyVerdict(true, "BLOCKED", 0L,
                List.of(new QuotePolicyVerdict.Breach(7L, 1, "MARGIN_FLOOR", "15", "20"))));

        assertThatThrownBy(() -> approvalService.submitForApproval(42L, command()))
                .isInstanceOfSatisfying(com.opencrm.common.exception.BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("POLICY_BLOCKED");
                    assertThat(ex.getMessage()).contains("15").contains("20");
                });
        verify(approvalRoutingClient, never()).submit(any());
        verify(quoteRepository, never()).save(any());
    }

    @Test
    void requiresApprovalStillSubmitsThroughTheBff() {
        Quote quote = draft();
        when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote));
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(quotePolicyEvaluation.evaluate(quote)).thenReturn(new QuotePolicyVerdict(true, "REQUIRES_APPROVAL", 0L,
                List.of(new QuotePolicyVerdict.Breach(7L, 1, "DISCOUNT_PERCENT", "32", "25"))));
        when(approvalRoutingClient.submit(any(RoutingApprovalSubmitRequest.class))).thenReturn(pending());
        when(approvalRoutingClient.getById(9001L)).thenReturn(pending());

        ApprovalRequestDto dto = approvalService.submitForApproval(42L, command());

        verify(approvalRoutingClient).submit(any(RoutingApprovalSubmitRequest.class));
        verify(approvalRepository, never()).save(any());
        assertThat(dto.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
    }

    private static Quote draft() {
        Quote quote = Quote.builder().quoteNumber("Q-100").status(QuoteStatus.DRAFT).build();
        quote.setId(42L);
        quote.setVersion(0L);
        quote.setUuid("quote-uuid");
        return quote;
    }

    private static ApprovalSubmitRequest command() {
        return ApprovalSubmitRequest.builder().tier(ApprovalTier.DEAL_DESK).expectedVersion(0L).build();
    }

    private static RoutingApprovalDto pending() {
        return RoutingApprovalDto.builder()
                .id(9001L)
                .quoteId("42")
                .status("PENDING")
                .approvalTier("DEAL_DESK")
                .requesterId("seller")
                .submittedAt(Instant.parse("2026-11-24T00:00:00Z"))
                .build();
    }
}
