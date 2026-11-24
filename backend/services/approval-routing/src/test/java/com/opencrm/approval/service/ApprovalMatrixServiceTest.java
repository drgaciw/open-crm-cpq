package com.opencrm.approval.service;

import com.opencrm.approval.domain.ApprovalMatrix;
import com.opencrm.approval.domain.ApprovalMatrixRule;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.MatrixStatus;
import com.opencrm.approval.domain.MatrixStepMode;
import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.approval.domain.ThresholdType;
import com.opencrm.approval.dto.ApprovalRequestDto;
import com.opencrm.approval.dto.DefineApprovalMatrixRequest;
import com.opencrm.approval.dto.MatrixRuleRequest;
import com.opencrm.approval.dto.PolicyBreachRequest;
import com.opencrm.approval.dto.SubmitForApprovalRequest;
import com.opencrm.approval.mapper.ApprovalRequestMapper;
import com.opencrm.approval.repository.ApprovalMatrixRepository;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.approval.verdict.PolicyVerdict;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ApprovalRequestedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Approval matrix define, submit, and inbox")
class ApprovalMatrixServiceTest {

    @Mock
    private ApprovalMatrixRepository matrices;
    @Mock
    private ApprovalRequestRepository requests;
    @Mock
    private ApprovalRequestMapper mapper;
    @Mock
    private OutboxEventPublisher outbox;

    private ApprovalMatrixService service;
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        service = new ApprovalMatrixService(matrices, requests, mapper, outbox,
                new CurrentActor("service:approval-routing", false));
        lenient().when(mapper.toDtoList(any())).thenAnswer(invocation -> {
            List<ApprovalRequest> rows = invocation.getArgument(0);
            return rows.stream().map(row -> ApprovalRequestDto.builder()
                    .id(row.getId())
                    .quoteId(row.getQuoteId())
                    .status(row.getStatus())
                    .approvalTier(row.getApprovalTier())
                    .matrixVersion(row.getMatrixVersion())
                    .routingStep(row.getRoutingStep())
                    .stepMode(row.getStepMode())
                    .tenantId(row.getTenantId())
                    .build()).toList();
        });
        lenient().when(requests.save(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest row = invocation.getArgument(0);
            if (row.getId() == null) {
                row.setId(ids.incrementAndGet());
            }
            return row;
        });
    }

    @AfterEach
    void signOut() {
        MatrixSessions.clear();
    }

    @Test
    @DisplayName("a revision becomes the next version and deprecates the predecessor")
    void revisionIncrementsTheVersion() {
        MatrixSessions.user("desk", MatrixSessions.TENANT_A, "DealDesk");
        ApprovalMatrix first = matrix(1L, 1);
        when(matrices.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(first));
        when(matrices.save(any(ApprovalMatrix.class))).thenAnswer(invocation -> {
            ApprovalMatrix saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(2L);
            }
            return saved;
        });

        var defined = service.define(DefineApprovalMatrixRequest.builder()
                .name("Discount route")
                .supersedesMatrixId(1L)
                .rules(List.of(rule(ThresholdType.DISCOUNT_PERCENT, 1, MatrixStepMode.SERIAL, QuoteApprovalTier.CFO)))
                .build());

        assertThat(defined.getMatrixVersion()).isEqualTo(2);
        assertThat(defined.getSupersedesMatrixId()).isEqualTo(1L);
        assertThat(first.getStatus()).isEqualTo(MatrixStatus.DEPRECATED);
        assertThat(defined.getTenantId()).isEqualTo(MatrixSessions.TENANT_A);
    }

    @Test
    @DisplayName("serial submit leaves the later step WAITING and emits ApprovalRequested")
    void serialSubmitCreatesWaitingStepAndEvent() {
        MatrixSessions.user("seller", MatrixSessions.TENANT_A, "CPQ.User");
        when(requests.findByQuoteIdAndDeletedFalseAndStatusIn(any(), any())).thenReturn(List.of());
        when(matrices.findByTenantIdAndStatusAndDeletedFalse(MatrixSessions.TENANT_A, MatrixStatus.ACTIVE))
                .thenReturn(List.of(serialMatrix()));

        List<ApprovalRequestDto> created = service.submitForApproval(submission(PolicyVerdict.REQUIRES_APPROVAL));

        assertThat(created).extracting(ApprovalRequestDto::getStatus)
                .containsExactly(ApprovalStatus.PENDING, ApprovalStatus.WAITING);
        assertThat(created).extracting(ApprovalRequestDto::getApprovalTier).containsExactly("DEAL_DESK", "SALES_VP");
        assertThat(created).allMatch(row -> row.getMatrixVersion() == 4);
        ArgumentCaptor<ApprovalRequestedEvent> event = ArgumentCaptor.forClass(ApprovalRequestedEvent.class);
        verify(outbox).publish(any(), any(), org.mockito.ArgumentMatchers.eq(EventContractRegistry.APPROVAL_REQUESTED_V1),
                event.capture());
        assertThat(event.getValue().steps()).extracting(ApprovalRequestedEvent.RequestedStep::status)
                .containsExactly("PENDING", "WAITING");
    }

    @Test
    @DisplayName("parallel submit makes every role in the step PENDING")
    void parallelSubmitIsPendingTogether() {
        MatrixSessions.user("seller", MatrixSessions.TENANT_A, "CPQ.User");
        when(requests.findByQuoteIdAndDeletedFalseAndStatusIn(any(), any())).thenReturn(List.of());
        when(matrices.findByTenantIdAndStatusAndDeletedFalse(MatrixSessions.TENANT_A, MatrixStatus.ACTIVE))
                .thenReturn(List.of(parallelMatrix()));

        List<ApprovalRequestDto> created = service.submitForApproval(submission(PolicyVerdict.REQUIRES_APPROVAL));

        assertThat(created).hasSize(2);
        assertThat(created).allMatch(row -> row.getStatus() == ApprovalStatus.PENDING);
        assertThat(created).extracting(ApprovalRequestDto::getStepMode).containsOnly(MatrixStepMode.PARALLEL);
    }

    @Test
    @DisplayName("Pass and Blocked write nothing")
    void passAndBlockedDoNotWrite() {
        MatrixSessions.user("seller", MatrixSessions.TENANT_A, "CPQ.User");
        assertThatThrownBy(() -> service.submitForApproval(submission(PolicyVerdict.PASS)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("NO_APPROVAL_REQUIRED");
        assertThatThrownBy(() -> service.submitForApproval(submission(PolicyVerdict.BLOCKED)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("POLICY_BLOCKED");
        verify(requests, never()).save(any());
        verify(outbox, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("another tenant and a role that cannot submit are refused")
    void crossTenantAndRoleNegatives() {
        MatrixSessions.user("seller", MatrixSessions.TENANT_A, "CPQ.User");
        SubmitForApprovalRequest otherTenant = submission(PolicyVerdict.REQUIRES_APPROVAL);
        otherTenant.setQuoteTenantId(MatrixSessions.TENANT_B);
        assertThatThrownBy(() -> service.submitForApproval(otherTenant))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus(), ex -> ((BusinessException) ex).getErrorCode())
                .containsExactly(HttpStatus.FORBIDDEN, "CROSS_TENANT");

        MatrixSessions.user("admin", MatrixSessions.TENANT_A, "Admin");
        assertThatThrownBy(() -> service.submitForApproval(submission(PolicyVerdict.REQUIRES_APPROVAL)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUESTER_ROLE_REQUIRED");

        MatrixSessions.service("quote-service");
        assertThatThrownBy(() -> service.submitForApproval(submission(PolicyVerdict.REQUIRES_APPROVAL)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUESTER_MUST_BE_A_USER");
        verify(requests, never()).save(any());
    }

    @Test
    @DisplayName("the inbox is the caller's tenant and only tiers they may decide")
    void inboxFiltersTenantAndRole() {
        ApprovalRequest dealDesk = pending("DEAL_DESK", MatrixSessions.TENANT_A);
        ApprovalRequest salesVp = pending("SALES_VP", MatrixSessions.TENANT_A);
        ApprovalRequest otherTenant = pending("DEAL_DESK", MatrixSessions.TENANT_B);
        when(requests.findPendingVisibleToTenant(MatrixSessions.TENANT_A)).thenReturn(List.of(dealDesk, salesVp, otherTenant));

        MatrixSessions.user("desk", MatrixSessions.TENANT_A, "DealDesk");
        assertThat(service.listPendingApprovals(null, null)).extracting(ApprovalRequestDto::getApprovalTier)
                .containsExactly("DEAL_DESK");

        assertThatThrownBy(() -> service.listPendingApprovals("CFO", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("APPROVER_ROLE_REQUIRED");
        assertThatThrownBy(() -> service.listPendingApprovals(null, MatrixSessions.TENANT_B))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("CROSS_TENANT");

        MatrixSessions.service("quote-service");
        assertThatThrownBy(() -> service.listPendingApprovals(null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUESTER_MUST_BE_A_USER");
    }

    private static SubmitForApprovalRequest submission(PolicyVerdict verdict) {
        return SubmitForApprovalRequest.builder()
                .quoteId("42")
                .quoteVersion("7")
                .verdict(verdict)
                .quoteTenantId(MatrixSessions.TENANT_A)
                .breaches(List.of(PolicyBreachRequest.builder()
                        .breachType("DISCOUNT_PERCENT")
                        .actual(new BigDecimal("30"))
                        .limit(new BigDecimal("10"))
                        .amount(new BigDecimal("25000"))
                        .build()))
                .build();
    }

    private static ApprovalMatrix serialMatrix() {
        ApprovalMatrix matrix = emptyMatrix(8L, 4);
        matrix.addRule(stored("DISCOUNT_PERCENT", 1, MatrixStepMode.SERIAL, QuoteApprovalTier.DEAL_DESK));
        matrix.addRule(stored("DISCOUNT_PERCENT", 2, MatrixStepMode.SERIAL, QuoteApprovalTier.SALES_VP));
        return matrix;
    }

    private static ApprovalMatrix parallelMatrix() {
        ApprovalMatrix matrix = emptyMatrix(8L, 4);
        matrix.addRule(stored("DISCOUNT_PERCENT", 1, MatrixStepMode.PARALLEL, QuoteApprovalTier.DEAL_DESK));
        matrix.addRule(stored("DISCOUNT_PERCENT", 1, MatrixStepMode.PARALLEL, QuoteApprovalTier.SALES_VP));
        return matrix;
    }

    private static ApprovalMatrix emptyMatrix(Long id, int version) {
        ApprovalMatrix matrix = ApprovalMatrix.builder()
                .name("route")
                .status(MatrixStatus.ACTIVE)
                .matrixVersion(version)
                .tenantId(MatrixSessions.TENANT_A)
                .build();
        matrix.setId(id);
        return matrix;
    }

    private static ApprovalMatrix matrix(Long id, int version) {
        ApprovalMatrix matrix = ApprovalMatrix.builder()
                .name("route")
                .status(MatrixStatus.ACTIVE)
                .matrixVersion(version)
                .tenantId(MatrixSessions.TENANT_A)
                .build();
        matrix.setId(id);
        matrix.addRule(stored("DISCOUNT_PERCENT", 1, MatrixStepMode.SERIAL, QuoteApprovalTier.DEAL_DESK));
        return matrix;
    }

    private static ApprovalMatrixRule stored(String type, int order, MatrixStepMode mode, QuoteApprovalTier role) {
        return ApprovalMatrixRule.builder()
                .breachType(type)
                .amountMin(BigDecimal.ZERO)
                .amountMax(new BigDecimal("100000"))
                .stepOrder(order)
                .stepMode(mode)
                .approverRole(role)
                .build();
    }

    private static MatrixRuleRequest rule(ThresholdType type, int order, MatrixStepMode mode, QuoteApprovalTier role) {
        return MatrixRuleRequest.builder()
                .breachType(type)
                .amountMin(BigDecimal.ZERO)
                .amountMax(new BigDecimal("100000"))
                .stepOrder(order)
                .stepMode(mode)
                .approverRole(role)
                .build();
    }

    private static ApprovalRequest pending(String tier, String tenant) {
        ApprovalRequest row = ApprovalRequest.builder()
                .quoteId("42")
                .approvalType(com.opencrm.approval.domain.ApprovalType.QUOTE)
                .status(ApprovalStatus.PENDING)
                .approvalTier(tier)
                .tenantId(tenant)
                .requesterId("someone-else")
                .build();
        row.setId(tenant.equals(MatrixSessions.TENANT_A) ? ("DEAL_DESK".equals(tier) ? 1L : 2L) : 3L);
        return row;
    }
}
