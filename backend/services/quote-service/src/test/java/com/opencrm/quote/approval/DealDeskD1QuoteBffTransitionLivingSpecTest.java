package com.opencrm.quote.approval;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.client.RoutingApprovalDto;
import com.opencrm.quote.client.RoutingApprovalSubmitRequest;
import com.opencrm.quote.quote.ApprovalState;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D1-1 living spec: Deal Desk deal lifecycle vs quote BFF + routing.
 *
 * <p>Vocabulary: source {@code DRAFT} → {@link QuoteStatus#DRAFT}; source {@code SUBMITTED} →
 * {@link QuoteStatus#PENDING_APPROVAL} + routing {@code PENDING}; source {@code APPROVED}/{@code REJECTED}
 * → quote + {@link ApprovalState} of the same name. BFF remains single-writer.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("D1-1 Deal Desk → Quote BFF transition living spec")
class DealDeskD1QuoteBffTransitionLivingSpecTest {

    @Mock
    private ApprovalRequestRepository approvalRepository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private ApprovalRoutingClient approvalRoutingClient;

    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(
                approvalRepository,
                quoteRepository,
                approvalRoutingClient,
                true,
                org.mockito.Mockito.mock(com.opencrm.quote.ramp.RampScheduleGuard.class)
        );
    }

    private Quote quote(QuoteStatus status) {
        Quote quote = Quote.builder().quoteNumber("Q-42").status(status).build();
        quote.setId(42L);
        return quote;
    }

    @Nested
    @DisplayName("T1 submit DRAFT → PENDING_APPROVAL (source DRAFT → SUBMITTED)")
    class Submit {

        @Test
        @DisplayName("DEAL_DESK submit writes routing only and projects quote PENDING")
        void submitDraft() {
            Quote quote = quote(QuoteStatus.DRAFT);
            when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote));
            when(quoteRepository.save(quote)).thenReturn(quote);
            when(approvalRoutingClient.submit(any(RoutingApprovalSubmitRequest.class)))
                    .thenReturn(RoutingApprovalDto.builder()
                            .id(9001L)
                            .quoteId("42")
                            .status("PENDING")
                            .approvalTier("DEAL_DESK")
                            .requesterId("rep-1")
                            .submittedAt(Instant.parse("2026-11-16T12:00:00Z"))
                            .build());

            ApprovalRequestDto dto = approvalService.submitForApproval(
                    42L,
                    ApprovalSubmitRequest.builder().tier(ApprovalTier.DEAL_DESK).build()
            );

            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
            assertThat(quote.getApprovalState()).isEqualTo(ApprovalState.PENDING);
            assertThat(quote.getApprovalTier()).isEqualTo("DEAL_DESK");
            assertThat(dto.getStatus()).isEqualTo(ApprovalStatus.PENDING);
            verify(approvalRepository, never()).save(any());
        }

        @Test
        @DisplayName("cannot submit from PENDING_APPROVAL (source cannot re-submit SUBMITTED)")
        void cannotSubmitPendingApproval() {
            when(quoteRepository.findByIdAndDeletedFalse(42L))
                    .thenReturn(Optional.of(quote(QuoteStatus.PENDING_APPROVAL)));

            assertThatThrownBy(() -> approvalService.submitForApproval(
                    42L,
                    ApprovalSubmitRequest.builder().tier(ApprovalTier.DEAL_DESK).build()
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo("INVALID_STATUS_TRANSITION");
            verify(approvalRoutingClient, never()).submit(any());
        }

        @Test
        @DisplayName("IN_REVIEW may submit (target analog; source DealServiceImpl is DRAFT-only)")
        void inReviewMaySubmit() {
            Quote quote = quote(QuoteStatus.IN_REVIEW);
            when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote));
            when(quoteRepository.save(quote)).thenReturn(quote);
            when(approvalRoutingClient.submit(any(RoutingApprovalSubmitRequest.class)))
                    .thenReturn(RoutingApprovalDto.builder()
                            .id(7L)
                            .quoteId("42")
                            .status("PENDING")
                            .approvalTier("DEAL_DESK")
                            .build());

            approvalService.submitForApproval(
                    42L,
                    ApprovalSubmitRequest.builder().tier(ApprovalTier.DEAL_DESK).build()
            );
            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
        }
    }

    @Nested
    @DisplayName("T2/T3 approve and reject PENDING")
    class Decide {

        @Test
        @DisplayName("T2: approve PENDING projects Quote APPROVED")
        void approvePending() {
            Quote quote = quote(QuoteStatus.PENDING_APPROVAL);
            quote.setApprovalState(ApprovalState.PENDING);
            when(approvalRoutingClient.getById(9001L)).thenReturn(RoutingApprovalDto.builder()
                    .id(9001L).quoteId("42").status("PENDING").build());
            when(approvalRoutingClient.approve(9001L)).thenReturn(RoutingApprovalDto.builder()
                    .id(9001L)
                    .quoteId("42")
                    .status("APPROVED")
                    .approverId("mgr-1")
                    .build());
            when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote));
            when(quoteRepository.save(quote)).thenReturn(quote);

            ApprovalRequestDto dto = approvalService.approveRequest(9001L, null);

            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.APPROVED);
            assertThat(quote.getApprovalState()).isEqualTo(ApprovalState.APPROVED);
            assertThat(dto.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
            verify(approvalRepository, never()).save(any());
        }

        @Test
        @DisplayName("T3: reject PENDING projects Quote REJECTED")
        void rejectPending() {
            Quote quote = quote(QuoteStatus.PENDING_APPROVAL);
            when(approvalRoutingClient.getById(9001L)).thenReturn(RoutingApprovalDto.builder()
                    .id(9001L).quoteId("42").status("PENDING").build());
            when(approvalRoutingClient.reject(9001L, "too steep")).thenReturn(RoutingApprovalDto.builder()
                    .id(9001L)
                    .quoteId("42")
                    .status("REJECTED")
                    .rejectionReason("too steep")
                    .build());
            when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote));
            when(quoteRepository.save(quote)).thenReturn(quote);

            ApprovalRequestDto dto = approvalService.rejectRequest(
                    9001L,
                    ApprovalResponseRequest.builder().comments("too steep").build()
            );

            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.REJECTED);
            assertThat(quote.getApprovalState()).isEqualTo(ApprovalState.REJECTED);
            assertThat(dto.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        }

        @Test
        @DisplayName("D1-2: blank reject reason is REJECT_REASON_REQUIRED 400, routing is not called")
        void rejectRequiresReason() {
            assertThatThrownBy(() -> approvalService.rejectRequest(9001L, null))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo("REJECT_REASON_REQUIRED");
                        assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                    });
            assertThatThrownBy(() -> approvalService.rejectRequest(
                    9001L, ApprovalResponseRequest.builder().comments("  ").build()))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo("REJECT_REASON_REQUIRED");
                    });
            verify(approvalRoutingClient, never()).reject(any(), any());
        }

        @Test
        @DisplayName("D1-2: routing INVALID_STATE_TRANSITION 409 stays 409, not APPROVAL_ROUTING_* 502")
        void invalidStateStaysFourXx() {
            when(approvalRoutingClient.getById(9001L)).thenReturn(RoutingApprovalDto.builder()
                    .id(9001L).quoteId("42").status("APPROVED").build());
            when(approvalRoutingClient.approve(9001L)).thenThrow(new BusinessException(
                    "Cannot approve approval in APPROVED (action=approve, currentStatus=APPROVED, targetStatus=APPROVED)",
                    org.springframework.http.HttpStatus.CONFLICT,
                    "INVALID_STATE_TRANSITION"));

            assertThatThrownBy(() -> approvalService.approveRequest(9001L, null))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                        assertThat(ex.getErrorCode()).isEqualTo("INVALID_STATE_TRANSITION");
                    });
        }
    }
}
