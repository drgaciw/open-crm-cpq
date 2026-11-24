package com.opencrm.approval.service;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.dto.ApprovalEscalatedEvent;
import com.opencrm.common.event.dto.ApprovalGrantedEvent;
import com.opencrm.common.event.dto.ApprovalRejected;
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
import com.opencrm.common.exception.ResourceNotFoundException;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

    private static final String TENANT = "tenant-a";

    private ApprovalRequest pendingRequest;

    private String signIn(String name, String... roles) {
        return TestCallers.signInUserInTenant(name, TENANT, null, roles);
    }

    @BeforeEach
    void setUp() {
        pendingRequest = pending("MANAGER", TestTokens.oidOf("rep-1"));

        approvalService.setClock(java.time.Clock.systemUTC());
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
        request.setTenantId(TENANT);
        lenient().when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        lenient().when(approvalRequestRepository.findScopedById(1L, TENANT, null)).thenReturn(Optional.of(request));
    }

    private static void assertRefused(HttpStatus status, String code, Runnable action) {
        BusinessException refused = assertThrows(BusinessException.class, action::run);
        assertEquals(status, refused.getStatus());
        assertEquals(code, refused.getErrorCode());
    }

    @Test
    @DisplayName("should approve a PENDING request, recording the authenticated approver's oid")
    void approvePendingRequest() {
        String manager = signIn("manager-1", "DealDesk");
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
        String manager = signIn("manager-1", "DealDesk");
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
        signIn("manager-2", "DealDesk");
        stubFind(pendingRequest);

        assertThrows(IllegalStateException.class, () -> approvalService.approve(1L));
    }

    @Test
    @DisplayName("decision 9: a superseded request answers 409 APPROVAL_SUPERSEDED, naming its replacement")
    void supersededRequestCannotBeDecided() {
        signIn("manager-1", "DealDesk");
        pendingRequest.supersede(2L);
        stubFind(pendingRequest);

        assertRefused(HttpStatus.CONFLICT, "APPROVAL_SUPERSEDED", () -> approvalService.approve(1L));
        assertRefused(HttpStatus.CONFLICT, "APPROVAL_SUPERSEDED", () -> approvalService.reject(1L, "no"));
        BusinessException refused = assertThrows(BusinessException.class, () -> approvalService.approve(1L));
        assertTrue(refused.getMessage().contains("approval 2"), refused.getMessage());
        verify(approvalRequestRepository, never()).save(any());
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    @DisplayName("decision 9: a caller who may not decide gets 403, not 409, so a superseded request's state stays private")
    void supersededStateIsNotRevealedToWhoMayNotDecide() {
        signIn("someone", "CPQ.User");
        pendingRequest.supersede(2L);
        stubFind(pendingRequest);

        assertRefused(HttpStatus.FORBIDDEN, "APPROVER_ROLE_REQUIRED", () -> approvalService.approve(1L));
    }

    @Test
    @DisplayName("decision 9: superseded requests are not in the pending lists")
    void supersededRequestsAreNotPending() {
        signIn("manager-1", "DealDesk");
        when(approvalRequestRepository.findScopedByStatus(ApprovalStatus.PENDING, TENANT, null))
                .thenReturn(List.of(pendingRequest));

        assertEquals(1, approvalService.getPendingApprovals().size());
        verify(approvalRequestRepository, never()).findByStatus(ApprovalStatus.SUPERSEDED);
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
            signIn("approver", role);
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
            signIn("rep-1", "CFO");
            stubFind(pendingRequest);

            assertRefused(HttpStatus.FORBIDDEN, "SELF_APPROVAL", () -> approvalService.approve(1L));
            assertRefused(HttpStatus.FORBIDDEN, "SELF_APPROVAL", () -> approvalService.reject(1L, "no"));
            verify(approvalRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("a service token cannot decide: approvals are human decisions")
        void servicesCannotDecide() {
            TestCallers.signInServiceInTenant("quote-service", TENANT, "Internal.Invoke");
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
        signIn("manager-1", "DealDesk");
        when(approvalRequestRepository.findScopedByStatus(ApprovalStatus.PENDING, TENANT, null))
                .thenReturn(List.of(pendingRequest));

        List<ApprovalRequestDto> result = approvalService.getPendingApprovals();

        assertEquals(1, result.size());
        assertEquals(ApprovalStatus.PENDING, result.get(0).getStatus());
    }

    @Test
    @DisplayName("should list pending approvals by tier")
    void listPendingByTier() {
        signIn("manager-1", "DealDesk");
        when(approvalRequestRepository.findScopedByTierAndStatus("MANAGER", ApprovalStatus.PENDING, TENANT, null))
                .thenReturn(List.of(pendingRequest));

        List<ApprovalRequestDto> result = approvalService.getPendingApprovalsByTier("MANAGER");

        assertEquals(1, result.size());
        assertEquals("MANAGER", result.get(0).getApprovalTier());
    }

    @Test
    @DisplayName("submit DEAL_DESK → PENDING QUOTE approval, requested by the authenticated user")
    void submitDealDeskCreatesPendingQuoteApproval() {
        String rep = signIn("rep-1", "CPQ.User");
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
        assertEquals(TENANT, savedTenant());
        assertEquals("Margin below 35%", result.getReason());
        assertNotNull(result.getSubmittedAt());
        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("submit by a service token records service:<client-id> and that token's tenant")
    void submitRecordsServiceActors() {
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TestCallers.signInServiceInTenant("quote-client-id", TENANT);
        ApprovalRequestDto created = approvalService.submit(ApprovalSubmitRequest.builder()
                .quoteId("Q-77").tier(QuoteApprovalTier.SALES_VP).build());
        assertEquals("service:quote-client-id", created.getRequesterId());
        assertEquals(TENANT, savedTenant());

        TestCallers.clear();
        assertRefused(HttpStatus.FORBIDDEN, "TENANT_REQUIRED", () -> approvalService.submit(ApprovalSubmitRequest.builder()
                .quoteId("Q-77").tier(QuoteApprovalTier.SALES_VP).build()));
    }

    @Test
    @DisplayName("dev mode: a submit with no caller records this service and a null tenant")
    void devModeSubmitWithoutCallerStoresNullTenant() {
        ApprovalService devService = new ApprovalService(approvalRequestRepository, outboxEventPublisher,
                approvalRequestMapper, new CurrentActor("service:approval-routing", true));
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequestDto result = devService.submit(ApprovalSubmitRequest.builder()
                .quoteId("Q-77").tier(QuoteApprovalTier.SALES_VP).build());

        assertEquals("service:approval-routing", result.getRequesterId());
        assertNull(savedTenant());
    }

    @Test
    @DisplayName("submit CFO → PENDING GOVERNANCE approval")
    void submitCfoCreatesPendingGovernanceApproval() {
        signIn("cfo-1", "CFO");
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
        signIn("manager-1", "DealDesk");
        when(approvalRequestRepository.findScopedByQuoteId("Q-001", TENANT, null))
                .thenReturn(List.of(pendingRequest, approved, rejected));

        List<ApprovalRequestDto> history = approvalService.getByQuoteId("Q-001");

        assertEquals(List.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED, ApprovalStatus.REJECTED),
                history.stream().map(ApprovalRequestDto::getStatus).toList());
        assertTrue(history.stream().allMatch(row -> "Q-001".equals(row.getQuoteId())));
        verify(approvalRequestRepository, never()).findByStatus(any());
    }

    @Test
    void historyForQuoteWithoutApprovalsIsEmpty() {
        signIn("manager-1", "DealDesk");
        when(approvalRequestRepository.findScopedByQuoteId("unknown", TENANT, null))
                .thenReturn(List.of());
        assertTrue(approvalService.getByQuoteId("unknown").isEmpty());
    }

    @Nested
    @DisplayName("DRG-444 approve, reject, and escalate")
    class Decisions {

        private final Instant now = Instant.parse("2026-11-24T12:00:00Z");

        @BeforeEach
        void pinClock() {
            approvalService.setClock(Clock.fixed(now, ZoneOffset.UTC));
            approvalService.setSlaWindow(Duration.ofHours(24));
        }

        @Test
        @DisplayName("approve stores the comment and emits ApprovalGranted")
        void approveStoresCommentAndEmitsGranted() {
            // Also covers the DRG-444 test-first specs (#1076) approveRecordsCommentsAndApprover and
            // approveEmitsApprovalGranted: the comment and the deciding approver are recorded and emitted.
            String approver = signIn("manager-1", "DealDesk");
            stubFind(pendingRequest);
            when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ApprovalRequestDto result = approvalService.approve(1L, "ship it");

            assertEquals(ApprovalStatus.APPROVED, result.getStatus());
            assertEquals("ship it", result.getDecisionComment());
            assertEquals(approver, result.getApproverId());
            ArgumentCaptor<ApprovalGrantedEvent> event = ArgumentCaptor.forClass(ApprovalGrantedEvent.class);
            verify(outboxEventPublisher).publish(
                    eq("ApprovalRequest"), eq("Q-001"), eq(EventContractRegistry.APPROVAL_GRANTED_V1), event.capture());
            assertEquals(pendingRequest.getId(), event.getValue().approvalId());
            assertEquals(approver, event.getValue().approverId());
            assertEquals("ship it", event.getValue().comment());
            assertEquals("Q-001", event.getValue().quoteId());
            assertEquals("MANAGER", event.getValue().approvalTier());
        }

        @Test
        @DisplayName("reject stores the comment and emits ApprovalRejected plus the legacy contract")
        void rejectStoresCommentAndEmitsRejected() {
            // Also covers the DRG-444 test-first specs (#1076) rejectRecordsCommentsAndApprover and
            // rejectEmitsApprovalRejected. Unlike the spec's single event, reject also keeps the legacy contract.
            String approver = signIn("manager-1", "DealDesk");
            stubFind(pendingRequest);
            when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ApprovalRequestDto result = approvalService.reject(1L, "revise the discount");

            assertEquals(ApprovalStatus.REJECTED, result.getStatus());
            assertEquals("revise the discount", result.getDecisionComment());
            assertEquals("revise the discount", result.getRejectionReason());
            assertEquals(approver, result.getApproverId());
            ArgumentCaptor<ApprovalRejected> event = ArgumentCaptor.forClass(ApprovalRejected.class);
            verify(outboxEventPublisher).publish(
                    eq("ApprovalRequest"), eq("Q-001"), eq(EventContractRegistry.APPROVAL_REJECTED_V1), event.capture());
            assertEquals(pendingRequest.getId(), event.getValue().approvalId());
            assertEquals("Q-001", event.getValue().quoteId());
            assertEquals(approver, event.getValue().approverId());
            assertEquals("revise the discount", event.getValue().comment());
            verify(outboxEventPublisher).publish(
                    eq("ApprovalRequest"), eq("Q-001"), eq(EventContractRegistry.QUOTE_APPROVAL_REJECTED_V1), any());
        }

        @Test
        @DisplayName("a role that cannot decide the tier is refused")
        void wrongRoleCannotApproveRejectOrEscalate() {
            signIn("someone", "CPQ.User");
            pendingRequest.setSlaDeadline(now.minusSeconds(60));
            stubFind(pendingRequest);

            assertRefused(HttpStatus.FORBIDDEN, "APPROVER_ROLE_REQUIRED", () -> approvalService.approve(1L, "no"));
            assertRefused(HttpStatus.FORBIDDEN, "APPROVER_ROLE_REQUIRED", () -> approvalService.reject(1L, "no"));
            assertRefused(HttpStatus.FORBIDDEN, "APPROVER_ROLE_REQUIRED", () -> approvalService.escalate(1L));
            verify(approvalRequestRepository, never()).save(any());
            verifyNoInteractions(outboxEventPublisher);
        }

        @Test
        @DisplayName("escalation before the SLA window is refused")
        void escalateBeforeWindowIsRefused() {
            signIn("manager-1", "DealDesk");
            pendingRequest.setSlaDeadline(now.plusSeconds(60));
            stubFind(pendingRequest);

            assertRefused(HttpStatus.CONFLICT, "SLA_WINDOW_OPEN", () -> approvalService.escalate(1L));
            verify(approvalRequestRepository, never()).save(any());
            verifyNoInteractions(outboxEventPublisher);
        }

        @Test
        @DisplayName("after the SLA window the request follows the escalation path and emits ApprovalEscalated")
        void escalateAfterWindowFollowsPath() {
            signIn("manager-1", "DealDesk");
            pendingRequest.setApprovalTier("DEAL_DESK");
            pendingRequest.setEscalationPath("DEAL_DESK,SALES_VP,CFO");
            pendingRequest.setSlaDeadline(now.minusSeconds(1));
            stubFind(pendingRequest);
            when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ApprovalRequestDto result = approvalService.escalate(1L);

            assertEquals(ApprovalStatus.PENDING, result.getStatus());
            assertEquals("SALES_VP", result.getApprovalTier());
            assertEquals("DEAL_DESK", result.getEscalatedFromTier());
            assertEquals(now, result.getEscalatedAt());
            assertEquals(now.plus(Duration.ofHours(24)), result.getSlaDeadline());
            ArgumentCaptor<ApprovalEscalatedEvent> event = ArgumentCaptor.forClass(ApprovalEscalatedEvent.class);
            verify(outboxEventPublisher).publish(
                    eq("ApprovalRequest"), eq("Q-001"), eq(EventContractRegistry.APPROVAL_ESCALATED_V1), event.capture());
            // Also covers #1076's escalateFollowsEscalationPathAfterSlaWindow and escalateEmitsApprovalEscalated.
            assertEquals(pendingRequest.getId(), event.getValue().approvalId());
            assertEquals("Q-001", event.getValue().quoteId());
            assertEquals("DEAL_DESK", event.getValue().fromTier());
            assertEquals("SALES_VP", event.getValue().toTier());
        }

        @Test
        @DisplayName("a tier with no further step is not escalated")
        void escalateAtEndOfPathIsRefused() {
            signIn("cfo-1", "CFO");
            pendingRequest.setApprovalTier("CFO");
            pendingRequest.setEscalationPath("DEAL_DESK,SALES_VP,CFO");
            pendingRequest.setSlaDeadline(now.minusSeconds(1));
            stubFind(pendingRequest);

            assertRefused(HttpStatus.CONFLICT, "NO_ESCALATION_PATH", () -> approvalService.escalate(1L));
            verifyNoInteractions(outboxEventPublisher);
        }

        @Test
        @DisplayName("DealDesk cannot escalate a CFO request")
        void lowerRoleCannotEscalateHigherTier() {
            signIn("manager-1", "DealDesk");
            pendingRequest.setApprovalTier("CFO");
            pendingRequest.setSlaDeadline(now.minusSeconds(1));
            stubFind(pendingRequest);

            assertRefused(HttpStatus.FORBIDDEN, "APPROVER_ROLE_REQUIRED", () -> approvalService.escalate(1L));
        }

        @Test
        @DisplayName("submit stamps an SLA deadline and the escalation path")
        void submitStampsDeadlineAndPath() {
            signIn("rep-1", "CPQ.User");
            when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

            approvalService.submit(ApprovalSubmitRequest.builder()
                    .quoteId("1001")
                    .tier(QuoteApprovalTier.DEAL_DESK)
                    .escalationPath("DEAL_DESK,CFO")
                    .build());

            ArgumentCaptor<ApprovalRequest> saved = ArgumentCaptor.forClass(ApprovalRequest.class);
            verify(approvalRequestRepository).save(saved.capture());
            assertEquals(TENANT, saved.getValue().getTenantId());
            assertEquals("DEAL_DESK,CFO", saved.getValue().getEscalationPath());
            assertEquals(now.plus(Duration.ofHours(24)), saved.getValue().getSlaDeadline());
            verify(outboxEventPublisher, never()).publish(any(), any(), any(), any());
        }
    }

    private String savedTenant() {
        ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(approvalRequestRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue().getTenantId();
    }

    @Nested
    @DisplayName("H4 C16/C17 tenant and account isolation")
    class TenantIsolation {

        @BeforeEach
        void ownRow() {
            pendingRequest.setTenantId("tenant-b");
            pendingRequest.setAccountId("acct-b");
        }

        /**
         * No 403-vs-404 oracle on decisions: the tenant check runs before the role, self-approval and
         * people-only checks, so another tenant's id answers exactly like a missing id for every caller.
         */
        @Test
        @DisplayName("C16: another tenant's id is 404 like a missing id even without the role, for the requester, and for a service")
        void otherTenantDecisionLooksMissingBeforeRoleAndSelfChecks() {
            when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));
            when(approvalRequestRepository.findById(99L)).thenReturn(Optional.empty());

            signIn("someone", "CPQ.User");
            for (long id : new long[] {1L, 99L}) {
                assertRefused(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", () -> approvalService.approve(id));
                assertRefused(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", () -> approvalService.reject(id, "no"));
                assertRefused(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", () -> approvalService.escalate(id));
            }

            signIn("rep-1", "DealDesk");
            assertRefused(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", () -> approvalService.approve(1L));

            TestCallers.signInServiceInTenant("quote-service", TENANT, "Internal.Invoke");
            assertRefused(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", () -> approvalService.approve(1L));

            verify(approvalRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("C16: tenant A cannot read, approve, reject, or see tenant B in the inbox")
        void otherTenantIsNotFound() {
            signIn("manager-1", "DealDesk");
            when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));
            when(approvalRequestRepository.findScopedByStatus(ApprovalStatus.PENDING, TENANT, null))
                    .thenReturn(List.of());
            when(approvalRequestRepository.findScopedByQuoteId("Q-001", TENANT, null)).thenReturn(List.of());

            assertRefused(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", () -> approvalService.getById(1L));
            assertRefused(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", () -> approvalService.approve(1L));
            assertRefused(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", () -> approvalService.reject(1L, "no"));
            assertTrue(approvalService.getPendingApprovals().isEmpty());
            assertTrue(approvalService.getByQuoteId("Q-001").isEmpty());
            verify(approvalRequestRepository, never()).save(any());
            verify(approvalRequestRepository, never()).findByStatus(any());
        }

        @Test
        @DisplayName("C16: create stores the token tenant, not a value carried beside the command")
        void createStoresTokenTenant() {
            signIn("rep-1", "CPQ.User");
            when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

            approvalService.submit(ApprovalSubmitRequest.builder()
                    .quoteId("1001").tier(QuoteApprovalTier.DEAL_DESK).reason("tenant-b").build());

            assertEquals(TENANT, savedTenant());
            assertNull(savedAccount());
        }

        @Test
        @DisplayName("C17: an account-scoped caller cannot read, decide, or list another account")
        void otherAccountIsNotFound() {
            pendingRequest.setTenantId(TENANT);
            pendingRequest.setAccountId("acct-b");
            TestCallers.signInUserInTenant("manager-1", TENANT, "acct-a", "DealDesk");
            when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));
            when(approvalRequestRepository.findScopedByStatus(ApprovalStatus.PENDING, TENANT, "acct-a"))
                    .thenReturn(List.of());

            assertThrows(ResourceNotFoundException.class, () -> approvalService.getById(1L));
            assertThrows(ResourceNotFoundException.class, () -> approvalService.approve(1L));
            assertThrows(ResourceNotFoundException.class, () -> approvalService.reject(1L, "no"));
            assertTrue(approvalService.getPendingApprovals().isEmpty());
            verify(approvalRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("same tenant and account can read, approve, and see the inbox row")
        void sameTenantAndAccountSucceed() {
            pendingRequest.setTenantId(TENANT);
            pendingRequest.setAccountId("acct-a");
            TestCallers.signInUserInTenant("manager-1", TENANT, "acct-a", "DealDesk");
            when(approvalRequestRepository.findById(1L)).thenReturn(Optional.of(pendingRequest));
            when(approvalRequestRepository.findScopedByStatus(ApprovalStatus.PENDING, TENANT, "acct-a"))
                    .thenReturn(List.of(pendingRequest));
            when(approvalRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            assertEquals(1L, approvalService.getById(1L).getId());
            assertEquals(1, approvalService.getPendingApprovals().size());
            assertEquals(ApprovalStatus.APPROVED, approvalService.approve(1L).getStatus());
        }

        private String savedAccount() {
            ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
            verify(approvalRequestRepository, atLeastOnce()).save(captor.capture());
            return captor.getValue().getAccountId();
        }
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
                .decisionComment(a.getDecisionComment())
                .slaDeadline(a.getSlaDeadline())
                .escalationPath(a.getEscalationPath())
                .escalatedFromTier(a.getEscalatedFromTier())
                .escalatedAt(a.getEscalatedAt())
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
