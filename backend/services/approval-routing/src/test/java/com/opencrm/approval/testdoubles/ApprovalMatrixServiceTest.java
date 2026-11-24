package com.opencrm.approval.testdoubles;

import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.approval.domain.ThresholdType;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.ApprovalMatrixStore;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.ApprovalMatrixView;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.DefineApprovalMatrix;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.MatrixStep;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.StepMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;

/**
 * DRG-443 DefineApprovalMatrix. Red until Feature replaces {@link NotImplementedApprovalMatrix}.
 * Runs under {@code -Punit-tests} (no {@code @Tag("integration")}, no Docker).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DRG-443 DefineApprovalMatrix")
class ApprovalMatrixServiceTest {

    @Mock
    private ApprovalMatrixStore store;

    private NotImplementedApprovalMatrix service;

    @BeforeEach
    void setUp() {
        service = NotImplementedApprovalMatrix.defineOnly(store);
    }

    @Test
    @DisplayName("maps a breach type and deal-amount band to approver roles")
    void defineApprovalMatrix_mapsBreachTypeAndAmountToApproverRoles() {
        DefineApprovalMatrix command = new DefineApprovalMatrix(
                "discount-by-amount",
                ThresholdType.DISCOUNT_PERCENT,
                List.of(
                        new MatrixStep(1, StepMode.SERIAL, List.of(QuoteApprovalTier.DEAL_DESK),
                                new BigDecimal("0"), new BigDecimal("10000")),
                        new MatrixStep(1, StepMode.SERIAL, List.of(QuoteApprovalTier.CFO),
                                new BigDecimal("10000"), null)),
                null);

        ApprovalMatrixView defined = service.defineApprovalMatrix(command);

        verify(store).record(command);
        assertThat(defined.breachType()).isEqualTo(ThresholdType.DISCOUNT_PERCENT);
        assertThat(defined.steps()).containsExactlyElementsOf(command.steps());
    }

    @Test
    @DisplayName("supports serial steps, in sequence order")
    void defineApprovalMatrix_supportsSerialSteps() {
        DefineApprovalMatrix command = serialDiscountMatrix();

        ApprovalMatrixView defined = service.defineApprovalMatrix(command);

        verify(store).record(command);
        assertThat(defined.steps())
                .extracting(MatrixStep::sequence, MatrixStep::mode, MatrixStep::roles)
                .containsExactly(
                        tuple(1, StepMode.SERIAL, List.of(QuoteApprovalTier.DEAL_DESK)),
                        tuple(2, StepMode.SERIAL, List.of(QuoteApprovalTier.SALES_VP)),
                        tuple(3, StepMode.SERIAL, List.of(QuoteApprovalTier.CFO)));
    }

    @Test
    @DisplayName("supports parallel steps: every role on the step approves it")
    void defineApprovalMatrix_supportsParallelSteps() {
        DefineApprovalMatrix command = new DefineApprovalMatrix(
                "discount-parallel",
                ThresholdType.DISCOUNT_PERCENT,
                List.of(new MatrixStep(1, StepMode.PARALLEL,
                        List.of(QuoteApprovalTier.SALES_VP, QuoteApprovalTier.CFO), null, null)),
                null);

        ApprovalMatrixView defined = service.defineApprovalMatrix(command);

        verify(store).record(command);
        assertThat(defined.steps()).singleElement().satisfies(step -> {
            assertThat(step.sequence()).isEqualTo(1);
            assertThat(step.mode()).isEqualTo(StepMode.PARALLEL);
            assertThat(step.roles()).containsExactly(QuoteApprovalTier.SALES_VP, QuoteApprovalTier.CFO);
        });
    }

    @Test
    @DisplayName("is versioned: a revision is a new version and the prior version stays readable")
    void defineApprovalMatrix_isVersioned() {
        DefineApprovalMatrix firstCommand = serialDiscountMatrix();
        ApprovalMatrixView first = service.defineApprovalMatrix(firstCommand);
        DefineApprovalMatrix secondCommand = firstCommand.reviseFrom(first);
        ApprovalMatrixView second = service.defineApprovalMatrix(secondCommand);

        verify(store).record(firstCommand);
        verify(store).record(secondCommand);
        assertThat(first.version()).isEqualTo(1);
        assertThat(first.supersedesId()).isNull();
        assertThat(second.version()).isEqualTo(2);
        assertThat(second.supersedesId()).isEqualTo(first.id());
        assertThat(service.approvalMatrix(first.id(), first.version()))
                .usingRecursiveComparison()
                .isEqualTo(first);
    }

    private static DefineApprovalMatrix serialDiscountMatrix() {
        return new DefineApprovalMatrix(
                "discount-serial",
                ThresholdType.DISCOUNT_PERCENT,
                List.of(
                        new MatrixStep(1, StepMode.SERIAL, List.of(QuoteApprovalTier.DEAL_DESK), null, null),
                        new MatrixStep(2, StepMode.SERIAL, List.of(QuoteApprovalTier.SALES_VP), null, null),
                        new MatrixStep(3, StepMode.SERIAL, List.of(QuoteApprovalTier.CFO), null, null)),
                null);
    }
}
