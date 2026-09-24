package com.opencrm.approval.service;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.approval.dto.ApprovalRequestDto;
import com.opencrm.approval.dto.ApprovalSubmitRequest;
import com.opencrm.approval.mapper.ApprovalRequestMapper;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.security.CurrentActor;
import com.opencrm.security.test.TestCallers;
import com.opencrm.security.test.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 tests for Approval Routing state machine, and who may decide (ADR-016 decisions 6 and 7).
 * Validates PENDING → APPROVED/REJECTED transitions and Outbox event publishing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Approval Routing - State Machine")
class ApprovalServiceTest {

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private ApprovalRequestMapper approvalRequestMapper;

    @Spy
    private CurrentActor currentActor = new CurrentActor("service:approval-routing", false);

    @InjectMocks
    private ApprovalService approvalService;

    private ApprovalRequest pendingRequest;

    @BeforeEach
    void setUp() {
        pendingRequest = pending("MANAGER", TestTokens.oidOf("rep-1"));

        lenient().when(approvalRequestMapper.toDto(any(ApprovalRequest.class)))
                .thenAnswer(inv -> toDto(inv.getArgument(0)));
        lenient().when(approvalRequestMapper.toDtoList(anyList()))
                .thenAnswer(inv -> {
                    List<ApprovalRequest> list = inv.getArgument(0);
                    return list.stream().map(this::toDto).toList();
                });
    }

    @AfterEach
    void signOut() {
        TestCallers.clear();
    }

    private static ApprovalRequest pending(String tier, String requesterId) {
        ApprovalRequest request = ApprovalRequest.builder()
                .quoteId("Q-001")
                .lineId("L-001")
                .approvalType(ApprovalType.DISCOUNT)
                .status(ApprovalStatus.PENDING)
                .requestedDiscount(BigDecimal.valueOf(25.00))
                .marginDelta(BigDecimal.valueOf(15.0))
                .currencyCode("USD")
                .requesterId(requesterId)
                .submittedAt(Instant.now())
                .approvalTier(tier)
                .build();
        request.setId(1L);
        return request;
    }

    private void stubFind(ApprovalRequest request) {
        when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(request));
    }

    private static void assertRefused(HttpStatus status, String code, Runnable action) {
        BusinessException refused = assertThrows(BusinessException.class, action::run);
        assertEquals(status, refused.getStatus());
        assertEquals(code, refused.getErrorCode());
    }

    @Test
    @DisplayName("should approve a PENDING request, recording the authenticated approver's oid")
    void approvePendingRequest() {
        String manager = TestCallers.signInUser("manager-1", "DealDesk");
        stubFind(pendingRequest);
        when(approvalRequestRepository.save(any())).thenReturn(pendingRequest);

        ApprovalRequestDto result = approvalService.approve(1L);

        assertEquals(ApprovalStatus.APPROVED, result.getStatus());
        assertEquals(manager, result.getApproverId());
        assertNotNull(result.getApprovedAt());
    }

    @Test
    @DisplayName("should reject a PENDING request and publish outbox event")
    void rejectPendingRequest() {
        String manager = TestCallers.signInUser("manager-1", "DealDesk");
        stubFind(pendingRequest);
        when(approvalRequestRepository.save(any())).thenReturn(pendingRequest);

        ApprovalRequestDto result = approvalService.reject(1L, "Discount too high");

        assertEquals(ApprovalStatus.REJECTED, result.getStatus());
        assertEquals(manager, result.getApproverId());
        assertEquals("Discount too high", result.getRejectionReason());
        assertNotNull(result.getRejectedAt());

        verify(outboxEventPublisher).publish(
                eq("ApprovalRequest"),
                eq("Q-001"),
                eq(EventContractRegistry.QUOTE_APPROVAL_REJECTED_V1),
                any()
        );
    }

    @Test
    @DisplayName("should reject approving an already APPROVED request")
    void rejectDoubleApproval() {
        pendingRequest.approve(TestTokens.oidOf("manager-1"));
        TestCallers.signInUser("manager-2", "DealDesk");
        stubFind(pendingRequest);

        assertThrows(IllegalStateException.class, () -> approvalService.approve(1L));
    }

    @Nested
    @DisplayName("who may decide (ADR-016 decision 7)")
    class Authority {

        @ParameterizedTest(name = "{0} approval decided by {1}: allowed={2}")
        @CsvSource({
                "DEAL_DESK, DealDesk, true",
                "DEAL_DESK, SalesVP, true",
                "DEAL_DESK, CFO, true",
                "SALES_VP, DealDesk, false",
                "SALES_VP, SalesVP, true",
                "SALES_VP, CFO, true",
                "CFO, SalesVP, false",
                "CFO, CFO, true",
                "MANAGER, DealDesk, true",
                "DIRECTOR, DealDesk, false",
                "DIRECTOR, SalesVP, true",
                "VP_SALES, SalesVP, true",
                "DEAL_DESK, CPQ.User, false",
                "DEAL_DESK, Admin, false",
                "SOMETHING_NEW, CFO, false"
        })
        void roleMustSatisfyTheTier(String tier, String role, boolean allowed) {
            TestCallers.signInUser("approver", role);
            stubFind(pending(tier, TestTokens.oidOf("rep-1")));
            lenient().when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            if (allowed) {
                assertEquals(ApprovalStatus.APPROVED, approvalService.approve(1L).getStatus());
            } else {
                assertRefused(HttpStatus.FORBIDDEN, "APPROVER_ROLE_REQUIRED", () -> approvalService.approve(1L));
                verify(approvalRequestRepository, never()).save(any());
            }
        }

        @Test
        @DisplayName("the requester cannot decide their own approval, even with the role")
        void noSelfApproval() {
            TestCallers.signInUser("rep-1", "CFO");
            stubFind(pendingRequest);

            assertRefused(HttpStatus.FORBIDDEN, "SELF_APPROVAL", () -> approvalService.approve(1L));
            assertRefused(HttpStatus.FORBIDDEN, "SELF_APPROVAL", () -> approvalService.reject(1L, "no"));
            verify(approvalRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("a service token cannot decide: approvals are human decisions")
        void servicesCannotDecide() {
            TestCallers.signInService("quote-service", "Internal.Invoke");
            stubFind(pendingRequest);

            assertRefused(HttpStatus.FORBIDDEN, "APPROVER_MUST_BE_A_USER", () -> approvalService.approve(1L));
        }

        @Test
        @DisplayName("without a verified caller the decision is refused with 401")
        void noCallerIsRefused() {
            stubFind(pendingRequest);

            assertRefused(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", () -> approvalService.approve(1L));
        }

        @Test
        @DisplayName("dev mode: without a caller the decision goes through, recorded as this service")
        void devModeRecordsTheService() {
            ApprovalService devService = new ApprovalService(approvalRequestRepository, outboxEventPublisher,
                    approvalRequestMapper, new CurrentActor("service:approval-routing", true));
            stubFind(pendingRequest);
            when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertEquals("service:approval-routing", devService.approve(1L).getApproverId());
        }
    }

    @Test
    @DisplayName("should list pending approvals")
    void listPendingApprovals() {
        when(approvalRequestRepository.findByStatus(ApprovalStatus.PENDING))
                .thenReturn(List.of(pendingRequest));

        List<ApprovalRequestDto> result = approvalService.getPendingApprovals();

        assertEquals(1, result.size());
        assertEquals(ApprovalStatus.PENDING, result.get(0).getStatus());
    }

    @Test
    @DisplayName("should list pending approvals by tier")
    void listPendingByTier() {
        when(approvalRequestRepository.findByApprovalTierAndStatus("MANAGER", ApprovalStatus.PENDING))
                .thenReturn(List.of(pendingRequest));

        List<ApprovalRequestDto> result = approvalService.getPendingApprovalsByTier("MANAGER");

        assertEquals(1, result.size());
        assertEquals("MANAGER", result.get(0).getApprovalTier());
    }

    @Test
    @DisplayName("submit DEAL_DESK → PENDING QUOTE approval, requested by the authenticated user")
    void submitDealDeskCreatesPendingQuoteApproval() {
        String rep = TestCallers.signInUser("rep-1", "CPQ.User");
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        ApprovalSubmitRequest request = ApprovalSubmitRequest.builder()
                .quoteId("1001")
                .tier(QuoteApprovalTier.DEAL_DESK)
                .reason("Margin below 35%")
                .build();

        ApprovalRequestDto result = approvalService.submit(request);

        assertEquals(42L, result.getId());
        assertEquals(ApprovalStatus.PENDING, result.getStatus());
        assertEquals("1001", result.getQuoteId());
        assertEquals("DEAL_DESK", result.getApprovalTier());
        assertEquals(ApprovalType.QUOTE, result.getApprovalType());
        assertEquals(rep, result.getRequesterId());
        assertEquals("Margin below 35%", result.getReason());
        assertNotNull(result.getSubmittedAt());
        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("submit by a service token records service:<client-id>; with no caller, this service")
    void submitRecordsServiceActors() {
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TestCallers.signInService("quote-client-id");
        assertEquals("service:quote-client-id", approvalService.submit(ApprovalSubmitRequest.builder()
                .quoteId("Q-77").tier(QuoteApprovalTier.SALES_VP).build()).getRequesterId());

        TestCallers.clear();
        ApprovalRequestDto result = approvalService.submit(ApprovalSubmitRequest.builder()
                .quoteId("Q-77")
                .tier(QuoteApprovalTier.SALES_VP)
                .build());

        assertEquals(ApprovalStatus.PENDING, result.getStatus());
        assertEquals("SALES_VP", result.getApprovalTier());
        assertEquals(ApprovalType.QUOTE, result.getApprovalType());
        assertEquals("service:approval-routing", result.getRequesterId());
    }

    @Test
    @DisplayName("submit CFO → PENDING GOVERNANCE approval")
    void submitCfoCreatesPendingGovernanceApproval() {
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        ApprovalRequestDto result = approvalService.submit(ApprovalSubmitRequest.builder()
                .quoteId("2002")
                .tier(QuoteApprovalTier.CFO)
                .requestedDiscount(BigDecimal.valueOf(40))
                .marginDelta(BigDecimal.valueOf(12.5))
                .currencyCode("USD")
                .build());

        ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(approvalRequestRepository).save(captor.capture());
        ApprovalRequest persisted = captor.getValue();

        assertEquals(ApprovalStatus.PENDING, result.getStatus());
        assertEquals("CFO", persisted.getApprovalTier());
        assertEquals(ApprovalType.GOVERNANCE, persisted.getApprovalType());
        assertEquals(BigDecimal.valueOf(40), persisted.getRequestedDiscount());
        assertEquals(BigDecimal.valueOf(12.5), persisted.getMarginDelta());
        assertEquals("USD", persisted.getCurrencyCode());
    }

    @Test
    @DisplayName("submit rejects blank quoteId")
    void submitRejectsBlankQuoteId() {
        assertThrows(BusinessException.class, () -> approvalService.submit(
                ApprovalSubmitRequest.builder().quoteId("  ").tier(QuoteApprovalTier.DEAL_DESK).build()));
        verify(approvalRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit rejects null request")
    void submitRejectsNullRequest() {
        assertThrows(BusinessException.class, () -> approvalService.submit(null));
        verify(approvalRequestRepository, never()).save(any());
    }


    @Test
    void historyReturnsAllStatesForRequestedQuote() {
        ApprovalRequest approved = ApprovalRequest.builder().id(2L).quoteId("Q-001")
                .approvalType(ApprovalType.QUOTE).status(ApprovalStatus.APPROVED).build();
        ApprovalRequest rejected = ApprovalRequest.builder().id(3L).quoteId("Q-001")
                .approvalType(ApprovalType.QUOTE).status(ApprovalStatus.REJECTED).build();
        when(approvalRequestRepository.findByQuoteIdAndDeletedFalseOrderBySubmittedAtDescIdDesc("Q-001"))
                .thenReturn(List.of(pendingRequest, approved, rejected));

        List<ApprovalRequestDto> history = approvalService.getByQuoteId("Q-001");

        assertEquals(List.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED, ApprovalStatus.REJECTED),
                history.stream().map(ApprovalRequestDto::getStatus).toList());
        assertTrue(history.stream().allMatch(row -> "Q-001".equals(row.getQuoteId())));
        verify(approvalRequestRepository, never()).findByStatus(any());
    }

    @Test
    void historyForQuoteWithoutApprovalsIsEmpty() {
        when(approvalRequestRepository.findByQuoteIdAndDeletedFalseOrderBySubmittedAtDescIdDesc("unknown"))
                .thenReturn(List.of());
        assertTrue(approvalService.getByQuoteId("unknown").isEmpty());
    }

    private ApprovalRequestDto toDto(ApprovalRequest a) {
        return ApprovalRequestDto.builder()
                .id(a.getId())
                .uuid(a.getUuid())
                .quoteId(a.getQuoteId())
                .lineId(a.getLineId())
                .approvalType(a.getApprovalType())
                .status(a.getStatus())
                .requestedDiscount(a.getRequestedDiscount())
                .marginDelta(a.getMarginDelta())
                .currencyCode(a.getCurrencyCode())
                .requesterId(a.getRequesterId())
                .approverId(a.getApproverId())
                .approvalTier(a.getApprovalTier())
                .reason(a.getReason())
                .rejectionReason(a.getRejectionReason())
                .submittedAt(a.getSubmittedAt())
                .approvedAt(a.getApprovedAt())
                .rejectedAt(a.getRejectedAt())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .createdBy(a.getCreatedBy())
                .updatedBy(a.getUpdatedBy())
                .version(a.getVersion())
                .build();
    }
}
