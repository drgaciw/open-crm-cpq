package com.opencrm.quote.approval;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.client.RoutingApprovalCommentRequest;
import com.opencrm.quote.client.RoutingApprovalDto;
import com.opencrm.quote.client.RoutingApprovalHistoryEntry;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteAccountResolver;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import com.opencrm.quote.quote.QuoteTenantResolver;
import com.opencrm.quote.ramp.RampScheduleGuard;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetApprovalHistory on the quote BFF")
class ApprovalHistoryBffTest {

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
                org.mockito.Mockito.mock(RampScheduleGuard.class));
    }

    @Test
    @DisplayName("a missing quote, another tenant, and another account are the same 404")
    void missingTenantAndAccountAreTheSameNotFound() {
        when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.empty());
        BusinessException missingHistory = catchThrowableOfType(BusinessException.class,
                () -> approvalService.getApprovalHistory(42L));
        BusinessException missingComment = catchThrowableOfType(BusinessException.class,
                () -> approvalService.addCaseComment(42L, 7L, ApprovalCommentRequest.builder().body("no").build()));

        Quote quote = quote("tenant-a", "acct-1", 4L);
        when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote));
        try (MockedStatic<QuoteTenantResolver> tenant = mockStatic(QuoteTenantResolver.class);
                MockedStatic<QuoteAccountResolver> account = mockStatic(QuoteAccountResolver.class)) {
            tenant.when(QuoteTenantResolver::currentTenantId).thenReturn("tenant-b");
            account.when(QuoteAccountResolver::currentAccountId).thenReturn("acct-1");
            assertSameNotFound(missingHistory, caughtHistory());
            assertSameNotFound(missingComment, caughtComment());

            tenant.when(QuoteTenantResolver::currentTenantId).thenReturn("tenant-a");
            account.when(QuoteAccountResolver::currentAccountId).thenReturn("acct-2");
            assertSameNotFound(missingHistory, caughtHistory());
            assertSameNotFound(missingComment, caughtComment());
        }

        verify(approvalRoutingClient, never()).getApprovalHistory(any());
        verify(approvalRoutingClient, never()).addComment(any(), any());
        verify(approvalRoutingClient, never()).getById(any());
    }

    @Test
    @DisplayName("the same tenant sees the timeline and a comment links the quote version")
    void sameTenantReadsHistoryAndStampsTheQuoteVersion() {
        Quote quote = quote("tenant-a", "acct-1", 4L);
        when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote));
        when(approvalRoutingClient.getById(7L)).thenReturn(RoutingApprovalDto.builder()
                .id(7L).quoteId("42").status("PENDING").build());
        when(approvalRoutingClient.getApprovalHistory(42L)).thenReturn(List.of(
                RoutingApprovalHistoryEntry.builder()
                        .id(1L).approvalId(7L).quoteId("42").kind("ESCALATED")
                        .eventName("ApprovalEscalated").quoteVersion(2L)
                        .occurredAt(Instant.parse("2026-11-24T12:00:10Z"))
                        .fromTier("DEAL_DESK").toTier("SALES_VP").build(),
                RoutingApprovalHistoryEntry.builder()
                        .id(2L).approvalId(9L).quoteId("99").kind("GRANTED").build()));
        when(approvalRoutingClient.addComment(eq(7L), any()))
                .thenReturn(RoutingApprovalHistoryEntry.builder()
                        .id(3L).approvalId(7L).quoteId("42").kind("COMMENT")
                        .commentId(100L).quoteVersion(4L).body("try 12 percent").build());

        try (MockedStatic<QuoteTenantResolver> tenant = mockStatic(QuoteTenantResolver.class);
                MockedStatic<QuoteAccountResolver> account = mockStatic(QuoteAccountResolver.class)) {
            tenant.when(QuoteTenantResolver::currentTenantId).thenReturn("tenant-a");
            account.when(QuoteAccountResolver::currentAccountId).thenReturn("acct-1");
            List<ApprovalHistoryEntryDto> rows = approvalService.getApprovalHistory(42L);
            ApprovalHistoryEntryDto comment = approvalService.addCaseComment(42L, 7L,
                    ApprovalCommentRequest.builder().body("try 12 percent").parentCommentId(null).build());

            assertThat(rows).extracting(ApprovalHistoryEntryDto::getQuoteId).containsExactly("42");
            assertThat(rows.getFirst().getEventName()).isEqualTo("ApprovalEscalated");
            assertThat(rows.getFirst().getQuoteVersion()).isEqualTo(2L);
            assertThat(comment.getCommentId()).isEqualTo(100L);
            assertThat(comment.getQuoteVersion()).isEqualTo(4L);
        }

        ArgumentCaptor<RoutingApprovalCommentRequest> sent = ArgumentCaptor.forClass(RoutingApprovalCommentRequest.class);
        verify(approvalRoutingClient).addComment(eq(7L), sent.capture());
        assertThat(sent.getValue().getQuoteVersion()).isEqualTo(4L);
        assertThat(sent.getValue().getBody()).isEqualTo("try 12 percent");
        verify(approvalRepository, never()).save(any());
    }

    private BusinessException caughtHistory() {
        return catchThrowableOfType(BusinessException.class, () -> approvalService.getApprovalHistory(42L));
    }

    private BusinessException caughtComment() {
        return catchThrowableOfType(BusinessException.class,
                () -> approvalService.addCaseComment(42L, 7L, ApprovalCommentRequest.builder().body("no").build()));
    }

    private static void assertSameNotFound(BusinessException missing, BusinessException refused) {
        assertThat(missing).isInstanceOf(ResourceNotFoundException.class);
        assertThat(refused).isInstanceOf(ResourceNotFoundException.class);
        assertThat(refused.getStatus()).isEqualTo(HttpStatus.NOT_FOUND).isEqualTo(missing.getStatus());
        assertThat(refused.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND").isEqualTo(missing.getErrorCode());
        assertThat(refused.getMessage()).isEqualTo("Quote not found with identifier: 42").isEqualTo(missing.getMessage());
        assertThat(refused.getMessage()).doesNotContain("CROSS_TENANT").doesNotContain("APPROVER_ROLE").doesNotContain("tier");
        assertThat(refused.getErrorCode()).doesNotContain("CROSS_TENANT").doesNotContain("ROLE");
    }

    private static Quote quote(String tenantId, String customerExternalId, Long version) {
        Quote quote = Quote.builder()
                .quoteNumber("Q-42")
                .status(QuoteStatus.PENDING_APPROVAL)
                .tenantId(tenantId)
                .customerExternalId(customerExternalId)
                .build();
        quote.setId(42L);
        quote.setVersion(version);
        return quote;
    }
}
