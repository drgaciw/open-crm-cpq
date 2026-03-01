package com.opencrm.approval.service;

import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.OutboxEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    }

    @Test
    @DisplayName("should approve a PENDING request")
    void approvePendingRequest() {
        when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));
        when(approvalRequestRepository.save(any())).thenReturn(pendingRequest);

        ApprovalRequest result = approvalService.approve(1L, "manager-1");

        assertEquals(ApprovalStatus.APPROVED, result.getStatus());
        assertEquals("manager-1", result.getApproverId());
        assertNotNull(result.getApprovedAt());
    }

    @Test
    @DisplayName("should reject a PENDING request and publish outbox event")
    void rejectPendingRequest() {
        when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));
        when(approvalRequestRepository.save(any())).thenReturn(pendingRequest);

        ApprovalRequest result = approvalService.reject(1L, "manager-1", "Discount too high");

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

        List<ApprovalRequest> result = approvalService.getPendingApprovals();

        assertEquals(1, result.size());
        assertEquals(ApprovalStatus.PENDING, result.get(0).getStatus());
    }

    @Test
    @DisplayName("should list pending approvals by tier")
    void listPendingByTier() {
        when(approvalRequestRepository.findByApprovalTierAndStatus("MANAGER", ApprovalStatus.PENDING))
                .thenReturn(List.of(pendingRequest));

        List<ApprovalRequest> result = approvalService.getPendingApprovalsByTier("MANAGER");

        assertEquals(1, result.size());
        assertEquals("MANAGER", result.get(0).getApprovalTier());
    }
}
