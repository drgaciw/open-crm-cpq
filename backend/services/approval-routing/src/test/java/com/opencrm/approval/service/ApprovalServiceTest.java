package com.opencrm.approval.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 tests for Approval Routing state machine.
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

    @InjectMocks
    private ApprovalService approvalService;

    private ApprovalRequest pendingRequest;

    @BeforeEach
    void setUp() {
        pendingRequest = ApprovalRequest.builder()
                .quoteId("Q-001")
                .lineId("L-001")
                .approvalType(ApprovalType.DISCOUNT)
                .status(ApprovalStatus.PENDING)
                .requestedDiscount(BigDecimal.valueOf(25.00))
                .marginDelta(BigDecimal.valueOf(15.0))
                .currencyCode("USD")
                .requesterId("user-1")
                .submittedAt(Instant.now())
                .approvalTier("MANAGER")
                .build();
        pendingRequest.setId(1L);

        lenient().when(approvalRequestMapper.toDto(any(ApprovalRequest.class)))
                .thenAnswer(inv -> toDto(inv.getArgument(0)));
        lenient().when(approvalRequestMapper.toDtoList(anyList()))
                .thenAnswer(inv -> {
                    List<ApprovalRequest> list = inv.getArgument(0);
                    return list.stream().map(this::toDto).toList();
                });
    }

    @Test
    @DisplayName("should approve a PENDING request")
    void approvePendingRequest() {
        when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));
        when(approvalRequestRepository.save(any())).thenReturn(pendingRequest);

        ApprovalRequestDto result = approvalService.approve(1L, "manager-1");

        assertEquals(ApprovalStatus.APPROVED, result.getStatus());
        assertEquals("manager-1", result.getApproverId());
        assertNotNull(result.getApprovedAt());
    }

    @Test
    @DisplayName("should reject a PENDING request and publish outbox event")
    void rejectPendingRequest() {
        when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));
        when(approvalRequestRepository.save(any())).thenReturn(pendingRequest);

        ApprovalRequestDto result = approvalService.reject(1L, "manager-1", "Discount too high");

        assertEquals(ApprovalStatus.REJECTED, result.getStatus());
        assertEquals("Discount too high", result.getRejectionReason());
        assertNotNull(result.getRejectedAt());

        verify(outboxEventPublisher).publish(
                eq("ApprovalRequest"),
                eq("Q-001"),
                eq("cpq.quote.approval.rejected"),
                any()
        );
    }

    @Test
    @DisplayName("should reject approving an already APPROVED request")
    void rejectDoubleApproval() {
        pendingRequest.approve("manager-1");
        when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));

        assertThrows(IllegalStateException.class, () -> approvalService.approve(1L, "manager-2"));
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
    @DisplayName("submit DEAL_DESK → PENDING QUOTE approval")
    void submitDealDeskCreatesPendingQuoteApproval() {
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        ApprovalSubmitRequest request = ApprovalSubmitRequest.builder()
                .quoteId("1001")
                .tier(QuoteApprovalTier.DEAL_DESK)
                .requesterId("rep-1")
                .reason("Margin below 35%")
                .build();

        ApprovalRequestDto result = approvalService.submit(request);

        assertEquals(42L, result.getId());
        assertEquals(ApprovalStatus.PENDING, result.getStatus());
        assertEquals("1001", result.getQuoteId());
        assertEquals("DEAL_DESK", result.getApprovalTier());
        assertEquals(ApprovalType.QUOTE, result.getApprovalType());
        assertEquals("rep-1", result.getRequesterId());
        assertEquals("Margin below 35%", result.getReason());
        assertNotNull(result.getSubmittedAt());
        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("submit SALES_VP → PENDING QUOTE with SALES_VP tier")
    void submitSalesVpCreatesPendingQuoteApproval() {
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequestDto result = approvalService.submit(ApprovalSubmitRequest.builder()
                .quoteId("Q-77")
                .tier(QuoteApprovalTier.SALES_VP)
                .build());

        assertEquals(ApprovalStatus.PENDING, result.getStatus());
        assertEquals("SALES_VP", result.getApprovalTier());
        assertEquals(ApprovalType.QUOTE, result.getApprovalType());
        assertEquals("system", result.getRequesterId());
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
                .requesterId("desk-9")
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
