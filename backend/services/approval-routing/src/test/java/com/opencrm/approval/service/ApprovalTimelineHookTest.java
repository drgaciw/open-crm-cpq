package com.opencrm.approval.service;

import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.dto.ApprovalRequestDto;
import com.opencrm.approval.mapper.ApprovalRequestMapper;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ApprovalEscalatedEvent;
import com.opencrm.common.event.dto.ApprovalGrantedEvent;
import com.opencrm.common.event.dto.ApprovalRejected;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.security.CurrentActor;
import com.opencrm.security.test.TestCallers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

@DisplayName("Decisions append the events they already emit to the timeline")
class ApprovalTimelineHookTest {

    private static final String TENANT = "tenant-a";
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    private final ApprovalRequestRepository repository = mock(ApprovalRequestRepository.class);
    private final OutboxEventPublisher publisher = mock(OutboxEventPublisher.class);
    private final ApprovalRequestMapper mapper = mock(ApprovalRequestMapper.class);
    private final ApprovalHistoryRecorder recorder = mock(ApprovalHistoryRecorder.class);
    private ApprovalService approvals;

    @BeforeEach
    void service() {
        approvals = new ApprovalService(repository, publisher, mapper,
                new CurrentActor("service:approval-routing", false));
        approvals.attachHistory(recorder);
        approvals.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        approvals.setSlaWindow(Duration.ofHours(24));
        when(mapper.toDto(any())).thenReturn(ApprovalRequestDto.builder().id(1L).build());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void signOut() {
        TestCallers.clear();
    }

    @Test
    void approveRecordsApprovalGrantedWithTheQuoteVersion() {
        TestCallers.signInUserInTenant("manager-1", TENANT, null, "DealDesk");
        when(repository.findById(1L)).thenReturn(Optional.of(pending()));

        approvals.approve(1L, "ship it", 5L);

        ArgumentCaptor<ApprovalGrantedEvent> event = ArgumentCaptor.forClass(ApprovalGrantedEvent.class);
        verify(recorder).recordGranted(event.capture(), eq(5L));
        assertEquals("ship it", event.getValue().comment());
        assertEquals("Q-001", event.getValue().quoteId());
        assertEquals(1L, event.getValue().approvalId());
    }

    @Test
    void rejectRecordsApprovalRejectedWithTheQuoteVersion() {
        TestCallers.signInUserInTenant("manager-1", TENANT, null, "DealDesk");
        when(repository.findById(1L)).thenReturn(Optional.of(pending()));

        approvals.reject(1L, "revise", 3L);

        ArgumentCaptor<ApprovalRejected> event = ArgumentCaptor.forClass(ApprovalRejected.class);
        verify(recorder).recordRejected(event.capture(), eq(3L));
        assertEquals("revise", event.getValue().comment());
    }

    @Test
    void escalateRecordsApprovalEscalatedWithTheQuoteVersion() {
        TestCallers.signInUserInTenant("manager-1", TENANT, null, "DealDesk");
        ApprovalRequest request = pending();
        request.setApprovalTier("DEAL_DESK");
        request.setEscalationPath("DEAL_DESK,SALES_VP");
        request.setSlaDeadline(NOW.minusSeconds(1));
        when(repository.findById(1L)).thenReturn(Optional.of(request));

        approvals.escalate(1L, 2L);

        ArgumentCaptor<ApprovalEscalatedEvent> event = ArgumentCaptor.forClass(ApprovalEscalatedEvent.class);
        verify(recorder).recordEscalated(event.capture(), eq(2L));
        assertEquals("DEAL_DESK", event.getValue().fromTier());
        assertEquals("SALES_VP", event.getValue().toTier());
    }

    @Test
    void aRefusedRoleDoesNotRecord() {
        TestCallers.signInUserInTenant("someone", TENANT, null, "CPQ.User");
        when(repository.findById(1L)).thenReturn(Optional.of(pending()));

        BusinessException refused = assertThrows(BusinessException.class, () -> approvals.approve(1L, "no", 1L));
        assertEquals("APPROVER_ROLE_REQUIRED", refused.getErrorCode());
        verifyNoInteractions(recorder);
    }

    private static ApprovalRequest pending() {
        ApprovalRequest request = ApprovalRequest.builder()
                .quoteId("Q-001")
                .tenantId(TENANT)
                .approvalType(ApprovalType.DISCOUNT)
                .status(ApprovalStatus.PENDING)
                .requesterId("seller")
                .approvalTier("MANAGER")
                .build();
        request.setId(1L);
        return request;
    }
}
