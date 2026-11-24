package com.opencrm.quote.approval;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.client.RoutingApprovalDto;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import com.opencrm.quote.ramp.RampScheduleGuard;
import com.opencrm.security.test.TestCallers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security H4, negative cases C16 and C17. There is no escalate write on the approval API;
 * approve and reject are the decision writes, and both are tenant-scoped.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalTenantIsolationTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Mock
    private ApprovalRequestRepository approvalRepository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private ApprovalRoutingClient approvalRoutingClient;
    @Mock
    private RampScheduleGuard rampScheduleGuard;

    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(
                approvalRepository, quoteRepository, approvalRoutingClient, true, rampScheduleGuard);
    }

    @AfterEach
    void signOut() {
        TestCallers.clear();
    }

    @Test
    @DisplayName("C16: tenant A cannot read, submit, approve or reject tenant B")
    void otherTenantIsNotFound() {
        TestCallers.signInUserInTenant("seller", TENANT_A, null, "DealDesk");
        Quote foreign = quote(42L, TENANT_B, "acct-b");
        when(quoteRepository.findByIdAndTenantIdAndDeletedFalse(42L, TENANT_A)).thenReturn(Optional.empty());
        when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(foreign));
        when(approvalRoutingClient.getById(7L)).thenReturn(routing(7L, "42", "PENDING"));

        assertThatThrownBy(() -> approvalService.getByQuoteId(42L))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("CROSS_TENANT"));
        assertThatThrownBy(() -> approvalService.submitForApproval(42L, submit()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("CROSS_TENANT"));
        assertThatThrownBy(() -> approvalService.approveRequest(7L, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("CROSS_TENANT"));
        assertThatThrownBy(() -> approvalService.rejectRequest(7L, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("CROSS_TENANT"));

        verify(approvalRoutingClient, never()).getByQuoteId(any());
        verify(approvalRoutingClient, never()).submit(any());
        verify(approvalRoutingClient, never()).approve(any());
        verify(approvalRoutingClient, never()).reject(any(), any());
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("C16: the inbox shows only the caller's tenant")
    void inboxIsOwnTenantOnly() {
        TestCallers.signInUserInTenant("seller", TENANT_A, null, "DealDesk");
        Quote own = quote(8L, TENANT_A, "acct-a");
        when(approvalRoutingClient.getPending()).thenReturn(List.of(
                routing(10L, "7", "PENDING"),
                routing(11L, "8", "PENDING")));
        when(quoteRepository.findByIdInAndTenantIdAndDeletedFalse(List.of(7L, 8L), TENANT_A)).thenReturn(List.of(own));
        ApprovalRequest localOtherTenant = local(100L, 7L);
        ApprovalRequest localOwn = local(101L, 8L);
        when(approvalRepository.findByStatusForTenant(ApprovalStatus.PENDING, TENANT_A, null))
                .thenReturn(List.of(localOwn));

        List<ApprovalRequestDto> pending = approvalService.getPendingApprovals();

        assertThat(pending).extracting(ApprovalRequestDto::getId).containsExactly(11L, 101L);
        assertThat(pending).extracting(ApprovalRequestDto::getQuoteId).containsOnly(8L);
        verify(approvalRepository, never()).findByStatusAndDeletedFalse(any());
        assertThat(localOtherTenant.getQuoteId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("C17: an account-scoped caller cannot read, submit, approve, reject or list another account")
    void otherAccountIsNotFound() {
        TestCallers.signInUserInTenant("seller", TENANT_A, "acct-a", "DealDesk");
        Quote otherAccount = quote(42L, TENANT_A, "acct-b");
        when(quoteRepository.findByIdAndTenantIdAndDeletedFalse(42L, TENANT_A)).thenReturn(Optional.of(otherAccount));
        when(approvalRoutingClient.getById(7L)).thenReturn(routing(7L, "42", "PENDING"));
        when(quoteRepository.findByIdInAndTenantIdAndDeletedFalse(List.of(42L), TENANT_A))
                .thenReturn(List.of(otherAccount));
        when(approvalRoutingClient.getPending()).thenReturn(List.of(routing(7L, "42", "PENDING")));
        when(approvalRepository.findByStatusForTenant(ApprovalStatus.PENDING, TENANT_A, "acct-a"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> approvalService.getByQuoteId(42L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> approvalService.submitForApproval(42L, submit())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> approvalService.approveRequest(7L, null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> approvalService.rejectRequest(7L, null)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(approvalService.getPendingApprovals()).isEmpty();
        verify(approvalRoutingClient, never()).approve(any());
        verify(approvalRoutingClient, never()).reject(any(), any());
        verify(approvalRoutingClient, never()).submit(any());
    }

    @Test
    @DisplayName("same tenant can submit, approve and see its own inbox row")
    void sameTenantSucceeds() {
        TestCallers.signInUserInTenant("manager", TENANT_A, null, "DealDesk");
        Quote own = quote(42L, TENANT_A, "acct-a");
        own.setVersion(3L);
        own.setStatus(QuoteStatus.DRAFT);
        when(quoteRepository.findByIdAndTenantIdAndDeletedFalse(42L, TENANT_A)).thenReturn(Optional.of(own));
        when(approvalRoutingClient.submit(any())).thenReturn(routing(9L, "42", "PENDING"));
        when(approvalRoutingClient.getById(9L)).thenReturn(routing(9L, "42", "PENDING"));
        when(quoteRepository.save(own)).thenReturn(own);

        ApprovalRequestDto submitted = approvalService.submitForApproval(42L, submit());

        assertThat(submitted.getId()).isEqualTo(9L);
        assertThat(own.getTenantId()).isEqualTo(TENANT_A);
        when(approvalRoutingClient.approve(9L)).thenReturn(routing(9L, "42", "APPROVED"));
        own.setStatus(QuoteStatus.PENDING_APPROVAL);
        ApprovalRequestDto approved = approvalService.approveRequest(9L, null);
        assertThat(approved.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(own.getStatus()).isEqualTo(QuoteStatus.APPROVED);
    }

    @Test
    @DisplayName("C17: the same tenant and account can read the quote's approvals")
    void sameAccountSucceeds() {
        TestCallers.signInUserInTenant("seller", TENANT_A, "acct-a", "CPQ.User");
        Quote own = quote(42L, TENANT_A, "acct-a");
        when(quoteRepository.findByIdAndTenantIdAndDeletedFalse(42L, TENANT_A)).thenReturn(Optional.of(own));
        when(approvalRoutingClient.getByQuoteId(42L)).thenReturn(List.of(routing(9L, "42", "PENDING")));

        assertThat(approvalService.getByQuoteId(42L)).extracting(ApprovalRequestDto::getId).containsExactly(9L);
    }

    @Test
    @DisplayName("a legacy row in another tenant is 404, not a conflict that admits it exists")
    void legacyRowInAnotherTenantIsNotFound() {
        TestCallers.signInUserInTenant("manager", TENANT_A, null, "DealDesk");
        when(approvalRoutingClient.approve(7L)).thenThrow(new ResourceNotFoundException("ApprovalRequest", 7L));
        when(approvalRepository.findVisibleById(7L, TENANT_A, null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.approveRequest(7L, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(approvalRepository, never()).findByIdAndDeletedFalse(any());
    }

    @Test
    @DisplayName("a legacy row in the caller's tenant stays read-only")
    void legacyRowInOwnTenantIsReadOnly() {
        TestCallers.signInUserInTenant("manager", TENANT_A, null, "DealDesk");
        when(approvalRoutingClient.approve(7L)).thenThrow(new ResourceNotFoundException("ApprovalRequest", 7L));
        when(approvalRepository.findVisibleById(7L, TENANT_A, null)).thenReturn(Optional.of(local(7L, 42L)));

        assertThatThrownBy(() -> approvalService.approveRequest(7L, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("LEGACY_APPROVAL_READ_ONLY"));
    }

    private static ApprovalSubmitRequest submit() {
        return ApprovalSubmitRequest.builder().tier(ApprovalTier.DEAL_DESK).expectedVersion(3L).build();
    }

    private static Quote quote(Long id, String tenantId, String accountId) {
        Quote quote = Quote.builder()
                .quoteNumber("Q-" + id)
                .name("Quote " + id)
                .customerExternalId(accountId)
                .tenantId(tenantId)
                .status(QuoteStatus.DRAFT)
                .build();
        quote.setId(id);
        quote.setVersion(3L);
        return quote;
    }

    private static RoutingApprovalDto routing(Long id, String quoteId, String status) {
        return RoutingApprovalDto.builder()
                .id(id)
                .quoteId(quoteId)
                .status(status)
                .approvalTier("DEAL_DESK")
                .build();
    }

    private static ApprovalRequest local(Long id, Long quoteId) {
        ApprovalRequest request = ApprovalRequest.builder()
                .quoteId(quoteId)
                .tier(ApprovalTier.CFO)
                .status(ApprovalStatus.PENDING)
                .requestedBy("legacy")
                .build();
        request.setId(id);
        return request;
    }
}
