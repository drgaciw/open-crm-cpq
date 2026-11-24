package com.opencrm.approval.service;

import com.opencrm.approval.domain.ApprovalMatrix;
import com.opencrm.approval.domain.ApprovalMatrixRule;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.MatrixStatus;
import com.opencrm.approval.domain.MatrixStepMode;
import com.opencrm.approval.domain.PolicyVerdict;
import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.approval.dto.ApprovalRequestDto;
import com.opencrm.approval.dto.PolicyBreachRequest;
import com.opencrm.approval.dto.SubmitForApprovalRequest;
import com.opencrm.approval.mapper.ApprovalRequestMapper;
import com.opencrm.approval.repository.ApprovalMatrixRepository;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ApprovalRequestedEvent;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DRG-443 SubmitForApproval, re-pointed from #1071 off {@code NotImplementedApprovalMatrix}
 * onto {@link ApprovalMatrixService}. The submit body has no approver tier.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DRG-443 SubmitForApproval")
class SubmitForApprovalMatrixTest {

    private static final long MATRIX_ID = 42L;
    private static final int MATRIX_VERSION = 3;
    private static final String QUOTE_ID = "Q-100";
    private static final String QUOTE_VERSION_ID = "QV-7";

    @Mock
    private ApprovalMatrixRepository matrices;
    @Mock
    private ApprovalRequestRepository requests;
    @Mock
    private ApprovalRequestMapper mapper;
    @Mock
    private OutboxEventPublisher outbox;

    private ApprovalMatrixService service;
    private final AtomicLong ids = new AtomicLong(500);

    @BeforeEach
    void setUp() {
        service = new ApprovalMatrixService(matrices, requests, mapper, outbox,
                new CurrentActor("service:approval-routing", false));
        lenient().when(requests.findByQuoteIdAndDeletedFalseAndStatusIn(any(), any())).thenReturn(List.of());
        lenient().when(matrices.findByIdAndDeletedFalse(MATRIX_ID)).thenReturn(Optional.of(twoStepMatrix()));
        lenient().when(requests.save(any(ApprovalRequest.class))).thenAnswer(invocation -> {
            ApprovalRequest row = invocation.getArgument(0);
            if (row.getId() == null) {
                row.setId(ids.incrementAndGet());
            }
            return row;
        });
        lenient().when(mapper.toDtoList(any())).thenAnswer(invocation -> {
            List<ApprovalRequest> rows = invocation.getArgument(0);
            return rows.stream().map(row -> ApprovalRequestDto.builder()
                    .id(row.getId())
                    .quoteId(row.getQuoteId())
                    .status(row.getStatus())
                    .approvalTier(row.getApprovalTier())
                    .routingStep(row.getRoutingStep())
                    .stepMode(row.getStepMode())
                    .matrixVersion(row.getMatrixVersion())
                    .build()).toList();
        });
        MatrixSessions.user("seller", MatrixSessions.TENANT_A, "CPQ.User");
    }

    @AfterEach
    void signOut() {
        MatrixSessions.clear();
    }

    @Test
    @DisplayName("creates one pending request per matrix step when the verdict requires approval")
    void submitForApproval_createsRequestsPerMatrixStep() {
        List<ApprovalRequestDto> created = service.submitForApproval(command(PolicyVerdict.REQUIRES_APPROVAL));

        verify(matrices).findByIdAndDeletedFalse(MATRIX_ID);
        assertThat(created)
                .extracting(ApprovalRequestDto::getQuoteId, ApprovalRequestDto::getApprovalTier,
                        ApprovalRequestDto::getRoutingStep, ApprovalRequestDto::getStepMode, ApprovalRequestDto::getStatus)
                .containsExactly(
                        tuple(QUOTE_ID, "DEAL_DESK", 1, MatrixStepMode.SERIAL, ApprovalStatus.PENDING),
                        tuple(QUOTE_ID, "SALES_VP", 2, MatrixStepMode.SERIAL, ApprovalStatus.PENDING));
    }

    @Test
    @DisplayName("does not create requests when the verdict is pass")
    void submitForApproval_doesNotCreateRequestsWhenVerdictIsPass() {
        assertThat(service.submitForApproval(command(PolicyVerdict.PASS))).isEmpty();

        verify(matrices).findByIdAndDeletedFalse(MATRIX_ID);
        verify(requests, never()).save(any());
        verify(outbox, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("emits ApprovalRequested for the quote version and the matrix")
    void submitForApproval_emitsApprovalRequested() {
        List<ApprovalRequestDto> created = service.submitForApproval(command(PolicyVerdict.REQUIRES_APPROVAL));

        ArgumentCaptor<ApprovalRequestedEvent> event = ArgumentCaptor.forClass(ApprovalRequestedEvent.class);
        verify(outbox).publish(any(), any(), org.mockito.ArgumentMatchers.eq(EventContractRegistry.APPROVAL_REQUESTED_V1),
                event.capture());
        assertThat(event.getValue().quoteId()).isEqualTo(QUOTE_ID);
        assertThat(event.getValue().quoteVersion()).isEqualTo(QUOTE_VERSION_ID);
        assertThat(event.getValue().matrixId()).isEqualTo(MATRIX_ID);
        assertThat(event.getValue().matrixVersion()).isEqualTo(MATRIX_VERSION);
        assertThat(event.getValue().approvalRequestIds())
                .containsExactlyElementsOf(created.stream().map(ApprovalRequestDto::getId).toList());
    }

    @Test
    @DisplayName("the submit body has no approver tier for the client to pick")
    void submitBodyDoesNotLetTheClientPickTheTier() {
        assertThat(java.util.Arrays.stream(SubmitForApprovalRequest.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("tier", "approvalTier", "approverRole", "approver");
    }

    private static SubmitForApprovalRequest command(PolicyVerdict verdict) {
        return SubmitForApprovalRequest.builder()
                .quoteId(QUOTE_ID)
                .quoteVersion(QUOTE_VERSION_ID)
                .verdict(verdict)
                .quoteTenantId(MatrixSessions.TENANT_A)
                .matrixId(MATRIX_ID)
                .matrixVersion(MATRIX_VERSION)
                .breaches(List.of(PolicyBreachRequest.builder()
                        .breachType("DISCOUNT_PERCENT")
                        .actual(new BigDecimal("25"))
                        .limit(new BigDecimal("15"))
                        .amount(new BigDecimal("25000"))
                        .build()))
                .build();
    }

    private static ApprovalMatrix twoStepMatrix() {
        ApprovalMatrix matrix = ApprovalMatrix.builder()
                .name("discount-serial")
                .status(MatrixStatus.ACTIVE)
                .matrixVersion(MATRIX_VERSION)
                .tenantId(MatrixSessions.TENANT_A)
                .build();
        matrix.setId(MATRIX_ID);
        matrix.addRule(rule(1, QuoteApprovalTier.DEAL_DESK));
        matrix.addRule(rule(2, QuoteApprovalTier.SALES_VP));
        return matrix;
    }

    private static ApprovalMatrixRule rule(int order, QuoteApprovalTier role) {
        return ApprovalMatrixRule.builder()
                .breachType("DISCOUNT_PERCENT")
                .stepOrder(order)
                .stepMode(MatrixStepMode.SERIAL)
                .approverRole(role)
                .build();
    }
}
