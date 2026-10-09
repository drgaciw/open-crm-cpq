package com.opencrm.quote.approval;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.client.RoutingApprovalDto;
import com.opencrm.quote.client.RoutingApprovalSubmitRequest;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR-016 step 4: the quote BFF sends no identity. approval-routing records the caller of the relayed token as
 * requester or approver and checks the approver's role against the tier; the BFF passes its answer through. Phase 0 had
 * the BFF resolve the actor itself; that code and its dev-only flag are gone.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalService: identity comes from approval-routing (ADR-016 step 4)")
class ApprovalServiceActorTest {

    @Mock
    private ApprovalRequestRepository approvalRepository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private ApprovalRoutingClient approvalRoutingClient;

    private ApprovalService service(boolean legacyLocalRead) {
        return new ApprovalService(approvalRepository, quoteRepository, approvalRoutingClient, legacyLocalRead, org.mockito.Mockito.mock(com.opencrm.quote.ramp.RampScheduleGuard.class));
    }

    private Quote quote(QuoteStatus status) {
        Quote quote = Quote.builder().quoteNumber("Q-42").status(status).build();
        quote.setId(42L);
        when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote));
        return quote;
    }

    @Test
    @DisplayName("approve relays the decision and shows the approver approval-routing recorded")
    void approveShowsTheRecordedApprover() {
        Quote quote = quote(QuoteStatus.PENDING_APPROVAL);
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(approvalRoutingClient.approve(7L)).thenReturn(RoutingApprovalDto.builder()
                .id(7L).quoteId("42").status("APPROVED").approvalTier("CFO").approverId("cfo-oid").build());

        ApprovalRequestDto dto = service(false).approveRequest(7L, ApprovalResponseRequest.builder().build());

        assertThat(dto.getApprover()).isEqualTo("cfo-oid");
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.APPROVED);
    }

    @Test
    @DisplayName("approval-routing's refusal (403 APPROVER_ROLE_REQUIRED) reaches the caller; the quote is untouched")
    void refusalPassesThrough() {
        when(approvalRoutingClient.reject(7L, "no")).thenThrow(new BusinessException(
                "Deciding a CFO approval needs one of the roles [ROLE_CFO]", HttpStatus.FORBIDDEN,
                "APPROVER_ROLE_REQUIRED"));

        assertThatThrownBy(() -> service(true).rejectRequest(
                7L, ApprovalResponseRequest.builder().comments("no").build()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode()).isEqualTo("APPROVER_ROLE_REQUIRED");
                });
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("a legacy local approval is history only: deciding it is 409, it would bypass the role check")
    void legacyLocalApprovalIsReadOnly() {
        when(approvalRoutingClient.approve(7L)).thenThrow(new ResourceNotFoundException("ApprovalRequest", 7L));
        when(approvalRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(new ApprovalRequest()));

        assertThatThrownBy(() -> service(true).approveRequest(7L, ApprovalResponseRequest.builder().build()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getErrorCode()).isEqualTo("LEGACY_APPROVAL_READ_ONLY");
                });
        verify(approvalRepository, never()).save(any());
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("an approval neither store knows is 404")
    void unknownApprovalIsNotFound() {
        when(approvalRoutingClient.reject(8L, null)).thenThrow(new ResourceNotFoundException("ApprovalRequest", 8L));
        when(approvalRepository.findByIdAndDeletedFalse(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(true).rejectRequest(8L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("submit names no requester; approval-routing records the token's user")
    void submitSendsNoRequester() {
        Quote quote = quote(QuoteStatus.DRAFT);
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(approvalRoutingClient.submit(any(RoutingApprovalSubmitRequest.class)))
                .thenReturn(RoutingApprovalDto.builder().id(1L).quoteId("42").status("PENDING")
                        .approvalTier("DEAL_DESK").requesterId("rep-oid").build());

        ApprovalRequestDto dto = service(false)
                .submitForApproval(42L, ApprovalSubmitRequest.builder().tier(ApprovalTier.DEAL_DESK).build());

        ArgumentCaptor<RoutingApprovalSubmitRequest> sent = ArgumentCaptor.forClass(RoutingApprovalSubmitRequest.class);
        verify(approvalRoutingClient).submit(sent.capture());
        assertThat(sent.getValue().getQuoteId()).isEqualTo("42");
        assertThat(sent.getValue().getTier()).isEqualTo("DEAL_DESK");
        assertThat(dto.getRequestedBy()).isEqualTo("rep-oid");
    }
}
