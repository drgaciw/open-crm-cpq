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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
@DisplayName("ApprovalService S2C-12 single-writer BFF")
class ApprovalServiceSingleWriterTest {

    @Mock
    private ApprovalRequestRepository approvalRepository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private ApprovalRoutingClient approvalRoutingClient;

    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        // No SecurityContext here, so these single-writer tests run with the dev-only
        // unverified-actor fallback on; actor resolution itself is in ApprovalServiceActorTest.
        approvalService = new ApprovalService(
                approvalRepository,
                quoteRepository,
                approvalRoutingClient,
                true
        );
    }

    @Test
    @DisplayName("submitForApproval writes routing only — never saves local ApprovalRequest")
    void submitDoesNotDualWriteLocalApprovalRequest() {
        Quote quote = Quote.builder()
                .quoteNumber("Q-100")
                .status(QuoteStatus.DRAFT)
                .build();
        quote.setId(42L);

        when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote));
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(approvalRoutingClient.submit(any(RoutingApprovalSubmitRequest.class)))
                .thenReturn(RoutingApprovalDto.builder()
                        .id(9001L)
                        .uuid("routing-uuid")
                        .quoteId("42")
                        .status("PENDING")
                        .approvalTier("DEAL_DESK")
                        .requesterId("agent-011")
                        .submittedAt(Instant.parse("2026-08-30T12:00:00Z"))
                        .createdAt(Instant.parse("2026-08-30T12:00:00Z"))
                        .build());

        ApprovalRequestDto dto = approvalService.submitForApproval(
                42L,
                ApprovalSubmitRequest.builder().tier(ApprovalTier.DEAL_DESK).build()
        );

        ArgumentCaptor<RoutingApprovalSubmitRequest> submitCaptor =
                ArgumentCaptor.forClass(RoutingApprovalSubmitRequest.class);
        verify(approvalRoutingClient).submit(submitCaptor.capture());
        assertThat(submitCaptor.getValue().getQuoteId()).isEqualTo("42");
        assertThat(submitCaptor.getValue().getTier()).isEqualTo("DEAL_DESK");

        verify(approvalRepository, never()).save(any());
        verify(approvalRepository, never()).findByIdAndDeletedFalse(any());
        verify(approvalRepository, never()).findByQuoteIdAndDeletedFalseOrderByCreatedAtDesc(any());
        verify(approvalRepository, never()).findByStatusAndDeletedFalse(any());

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
        assertThat(quote.getApprovalState()).isEqualTo(ApprovalState.PENDING);
        assertThat(quote.getApprovalTier()).isEqualTo("DEAL_DESK");
        verify(quoteRepository).save(quote);

        assertThat(dto.getId()).isEqualTo(9001L);
        assertThat(dto.getQuoteId()).isEqualTo(42L);
        assertThat(dto.getTier()).isEqualTo(ApprovalTier.DEAL_DESK);
        assertThat(dto.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(dto.getRequestedBy()).isEqualTo("agent-011");
    }

    @Test
    @DisplayName("getPendingApprovals prefers routing and falls back to legacy local without duplicate quote+tier")
    void getPendingDualReadPrefersRouting() {
        when(approvalRoutingClient.getPending()).thenReturn(List.of(
                RoutingApprovalDto.builder()
                        .id(10L)
                        .quoteId("7")
                        .status("PENDING")
                        .approvalTier("DEAL_DESK")
                        .requesterId("r1")
                        .build(),
                RoutingApprovalDto.builder()
                        .id(11L)
                        .quoteId("Q-DISC")
                        .status("PENDING")
                        .approvalTier("MANAGER")
                        .requesterId("r2")
                        .build()
        ));

        ApprovalRequest localSame = ApprovalRequest.builder()
                .quoteId(7L)
                .tier(ApprovalTier.DEAL_DESK)
                .status(ApprovalStatus.PENDING)
                .requestedBy("legacy")
                .build();
        localSame.setId(99L);

        ApprovalRequest localOnly = ApprovalRequest.builder()
                .quoteId(8L)
                .tier(ApprovalTier.CFO)
                .status(ApprovalStatus.PENDING)
                .requestedBy("legacy-only")
                .build();
        localOnly.setId(100L);

        when(approvalRepository.findByStatusAndDeletedFalse(ApprovalStatus.PENDING))
                .thenReturn(List.of(localSame, localOnly));

        List<ApprovalRequestDto> pending = approvalService.getPendingApprovals();

        assertThat(pending).extracting(ApprovalRequestDto::getId).containsExactly(10L, 100L);
        assertThat(pending).extracting(ApprovalRequestDto::getQuoteId).containsExactly(7L, 8L);
    }

    @Test
    @DisplayName("approveRequest updates Quote via routing and does not save local ApprovalRequest")
    void approveViaRoutingDoesNotWriteLocal() {
        when(approvalRoutingClient.approve(9001L))
                .thenReturn(RoutingApprovalDto.builder()
                        .id(9001L)
                        .quoteId("42")
                        .status("APPROVED")
                        .approvalTier("DEAL_DESK")
                        .approverId("mgr-1")
                        .approvedAt(Instant.parse("2026-08-30T13:00:00Z"))
                        .build());

        Quote quote = Quote.builder()
                .quoteNumber("Q-100")
                .status(QuoteStatus.PENDING_APPROVAL)
                .approvalState(ApprovalState.PENDING)
                .build();
        quote.setId(42L);
        when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote));
        when(quoteRepository.save(quote)).thenReturn(quote);

        ApprovalRequestDto dto = approvalService.approveRequest(
                9001L,
                ApprovalResponseRequest.builder().comments("ok").build()
        );

        verify(approvalRoutingClient).approve(9001L);
        verify(approvalRepository, never()).save(any());
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.APPROVED);
        assertThat(quote.getApprovalState()).isEqualTo(ApprovalState.APPROVED);
        assertThat(dto.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(dto.getApprover()).isEqualTo("mgr-1");
    }

    @Test
    @DisplayName("H7: flag-off pending read is routing-only — never queries local ApprovalRequest")
    void getPendingWithLegacyFlagOffDoesNotReadLocal() {
        ApprovalService routingOnly = new ApprovalService(
                approvalRepository,
                quoteRepository,
                approvalRoutingClient,
                false
        );
        when(approvalRoutingClient.getPending()).thenReturn(List.of(
                RoutingApprovalDto.builder()
                        .id(10L)
                        .quoteId("7")
                        .status("PENDING")
                        .approvalTier("DEAL_DESK")
                        .requesterId("r1")
                        .build()
        ));

        List<ApprovalRequestDto> pending = routingOnly.getPendingApprovals();

        assertThat(pending).extracting(ApprovalRequestDto::getId).containsExactly(10L);
        verify(approvalRepository, never()).findByStatusAndDeletedFalse(any());
        verify(approvalRepository, never()).findByQuoteIdAndDeletedFalseOrderByCreatedAtDesc(any());
    }

    @Test
    void historyRetainsDecisionsAndIsolatesQuoteWithLegacyDisabled() {
        ApprovalService routingOnly = new ApprovalService(
                approvalRepository, quoteRepository, approvalRoutingClient, false);
        when(approvalRoutingClient.getByQuoteId(42L)).thenReturn(List.of(
                historyRow(1L, "42", "PENDING"),
                historyRow(2L, "42", "APPROVED"),
                historyRow(3L, "42", "REJECTED"),
                historyRow(4L, "99", "PENDING")));

        List<ApprovalRequestDto> history = routingOnly.getByQuoteId(42L);

        assertThat(history).extracting(ApprovalRequestDto::getStatus)
                .containsExactly(ApprovalStatus.PENDING, ApprovalStatus.APPROVED, ApprovalStatus.REJECTED);
        assertThat(history).extracting(ApprovalRequestDto::getQuoteId).containsOnly(42L);
        verify(approvalRepository, never()).findByQuoteIdAndDeletedFalseOrderByCreatedAtDesc(any());
    }


    @Test
    void historyWithLegacyEnabledPrefersRoutingAndRetainsLocalDecisions() {
        when(approvalRoutingClient.getByQuoteId(42L)).thenReturn(List.of(
                historyRow(1L, "42", "PENDING"), historyRow(2L, "42", "APPROVED"),
                historyRow(3L, "42", "REJECTED")));
        ApprovalRequest duplicateId = ApprovalRequest.builder().id(2L).quoteId(42L)
                .tier(ApprovalTier.DEAL_DESK).status(ApprovalStatus.APPROVED).build();
        ApprovalRequest duplicatePending = ApprovalRequest.builder().id(99L).quoteId(42L)
                .tier(ApprovalTier.DEAL_DESK).status(ApprovalStatus.PENDING).build();
        ApprovalRequest legacyDecision = ApprovalRequest.builder().id(100L).quoteId(42L)
                .tier(ApprovalTier.CFO).status(ApprovalStatus.REJECTED).build();
        when(approvalRepository.findByQuoteIdAndDeletedFalseOrderByCreatedAtDesc(42L))
                .thenReturn(List.of(duplicateId, duplicatePending, legacyDecision));

        assertThat(approvalService.getByQuoteId(42L)).extracting(ApprovalRequestDto::getId)
                .containsExactly(1L, 2L, 3L, 100L);
        verify(approvalRoutingClient, never()).getPending();
        verify(approvalRepository, never()).save(any());
    }

    @Test
    void historyFallsBackOnRoutingFailureOnlyWhenLegacyEnabled() {
        BusinessException failure = new BusinessException("unavailable", "APPROVAL_ROUTING_UNAVAILABLE");
        when(approvalRoutingClient.getByQuoteId(42L)).thenThrow(failure);
        ApprovalRequest local = ApprovalRequest.builder().id(100L).quoteId(42L)
                .tier(ApprovalTier.CFO).status(ApprovalStatus.APPROVED).build();
        when(approvalRepository.findByQuoteIdAndDeletedFalseOrderByCreatedAtDesc(42L))
                .thenReturn(List.of(local));

        assertThat(approvalService.getByQuoteId(42L)).extracting(ApprovalRequestDto::getId)
                .containsExactly(100L);
    }

    @Test
    void historyPropagatesRoutingFailureWhenLegacyDisabled() {
        ApprovalService routingOnly = new ApprovalService(
                approvalRepository, quoteRepository, approvalRoutingClient, false);
        BusinessException failure = new BusinessException("unavailable", "APPROVAL_ROUTING_UNAVAILABLE");
        when(approvalRoutingClient.getByQuoteId(42L)).thenThrow(failure);

        assertThatThrownBy(() -> routingOnly.getByQuoteId(42L)).isSameAs(failure);
        verify(approvalRepository, never()).findByQuoteIdAndDeletedFalseOrderByCreatedAtDesc(any());
    }

    private RoutingApprovalDto historyRow(Long id, String quoteId, String status) {
        return RoutingApprovalDto.builder().id(id).quoteId(quoteId).status(status)
                .approvalTier("DEAL_DESK").build();
    }
}
