package com.opencrm.quote.approval;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.client.RoutingApprovalDto;
import com.opencrm.quote.client.RoutingApprovalSubmitRequest;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ADR-016 phase 0: the approval actor comes from the authenticated principal, never the body.
 * Before this change {@code resolveActor} preferred {@code ApprovalResponseRequest.approver},
 * so any caller could approve a CFO-tier quote as anyone.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalService actor resolution (ADR-016 phase 0)")
class ApprovalServiceActorTest {

    @Mock
    private ApprovalRequestRepository approvalRepository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private ApprovalRoutingClient approvalRoutingClient;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private ApprovalService service(boolean allowUnverifiedActor) {
        return new ApprovalService(approvalRepository, quoteRepository, approvalRoutingClient, false,
                allowUnverifiedActor);
    }

    private static void signInAs(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(userId, null, AuthorityUtils.NO_AUTHORITIES));
    }

    private void stubApprovedQuote() {
        when(approvalRoutingClient.approve(anyLong(), anyString())).thenAnswer(inv -> RoutingApprovalDto.builder()
                .id(inv.getArgument(0))
                .quoteId("42")
                .status("APPROVED")
                .approvalTier("CFO")
                .approverId(inv.getArgument(1))
                .build());
        Quote quote = Quote.builder().quoteNumber("Q-42").status(QuoteStatus.PENDING_APPROVAL).build();
        quote.setId(42L);
        when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote));
        when(quoteRepository.save(quote)).thenReturn(quote);
    }

    @Test
    @DisplayName("approve records the authenticated principal when the body names no one")
    void approveUsesPrincipal() {
        signInAs("cfo-oid");
        stubApprovedQuote();

        ApprovalRequestDto dto = service(false).approveRequest(7L, ApprovalResponseRequest.builder().build());

        verify(approvalRoutingClient).approve(7L, "cfo-oid");
        assertThat(dto.getApprover()).isEqualTo("cfo-oid");
    }

    @Test
    @DisplayName("a body approver equal to the principal is accepted")
    void matchingBodyApproverIsAccepted() {
        signInAs("cfo-oid");
        stubApprovedQuote();

        service(false).approveRequest(7L, ApprovalResponseRequest.builder().approver("cfo-oid").build());

        verify(approvalRoutingClient).approve(7L, "cfo-oid");
    }

    @Test
    @DisplayName("approve naming someone else is refused with 403 and never reaches routing")
    void spoofedApproverIsRefused() {
        signInAs("rep-42");

        assertThatThrownBy(() -> service(true).approveRequest(
                7L, ApprovalResponseRequest.builder().approver("cfo-oid").build()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode()).isEqualTo("ACTOR_MISMATCH");
                });
        verifyNoInteractions(approvalRoutingClient, approvalRepository, quoteRepository);
    }

    @Test
    @DisplayName("reject naming someone else is refused with 403")
    void spoofedRejecterIsRefused() {
        signInAs("rep-42");

        assertThatThrownBy(() -> service(false).rejectRequest(
                7L, ApprovalResponseRequest.builder().approver("cfo-oid").comments("no").build()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(approvalRoutingClient, never()).reject(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("with no principal and the flag off, approve is refused with 401")
    void noPrincipalIsRefusedByDefault() {
        assertThatThrownBy(() -> service(false).approveRequest(
                7L, ApprovalResponseRequest.builder().approver("cfo-oid").build()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getErrorCode()).isEqualTo("AUTHENTICATION_REQUIRED");
                });
        verifyNoInteractions(approvalRoutingClient);
    }

    @Test
    @DisplayName("an anonymous token is not a principal: refused with 401 when the flag is off")
    void anonymousIsRefusedByDefault() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(() -> service(false).approveRequest(7L, ApprovalResponseRequest.builder().build()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(approvalRoutingClient);
    }

    @Test
    @DisplayName("dev fallback: with no principal the body value is recorded, else system")
    void devFallbackRecordsUnverifiedLabel() {
        ApprovalService dev = service(true);

        assertThat(dev.resolveActor("mgr-1")).isEqualTo("mgr-1");
        assertThat(dev.resolveActor("  ")).isEqualTo("system");
        assertThat(dev.resolveActor(null)).isEqualTo("system");
    }

    @Test
    @DisplayName("submit sends the principal as requester and refuses a spoofed requestedBy")
    void submitUsesPrincipalAsRequester() {
        signInAs("rep-42");
        Quote quote = Quote.builder().quoteNumber("Q-42").status(QuoteStatus.DRAFT).build();
        quote.setId(42L);
        when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote));
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(approvalRoutingClient.submit(any(RoutingApprovalSubmitRequest.class)))
                .thenReturn(RoutingApprovalDto.builder().id(1L).quoteId("42").status("PENDING")
                        .approvalTier("DEAL_DESK").requesterId("rep-42").build());
        ApprovalService service = service(false);

        service.submitForApproval(42L, ApprovalSubmitRequest.builder().tier(ApprovalTier.DEAL_DESK).build());

        ArgumentCaptor<RoutingApprovalSubmitRequest> sent = ArgumentCaptor.forClass(RoutingApprovalSubmitRequest.class);
        verify(approvalRoutingClient).submit(sent.capture());
        assertThat(sent.getValue().getRequesterId()).isEqualTo("rep-42");

        quote.setStatus(QuoteStatus.DRAFT);
        assertThatThrownBy(() -> service.submitForApproval(42L,
                ApprovalSubmitRequest.builder().tier(ApprovalTier.DEAL_DESK).requestedBy("someone-else").build()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
