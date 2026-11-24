package com.opencrm.approval.testdoubles;

import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.approval.domain.ThresholdType;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.ApprovalMatrixView;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.CreatedApprovalRequest;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.MatrixLookup;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.MatrixStep;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.PolicyBreach;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.PolicyVerdict;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.StepMode;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.SubmitForApproval;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.SubmitForApprovalResult;
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
import static org.mockito.Mockito.when;

/**
 * DRG-443 SubmitForApproval. Red until Feature replaces {@link NotImplementedApprovalMatrix}.
 * One pending request per matrix step when the verdict requires approval, none on pass, and an
 * ApprovalRequested event when requests are created.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DRG-443 SubmitForApproval")
class SubmitForApprovalMatrixTest {

    private static final long MATRIX_ID = 42L;
    private static final int MATRIX_VERSION = 3;
    private static final String QUOTE_ID = "Q-100";
    private static final String QUOTE_VERSION_ID = "QV-7";

    @Mock
    private MatrixLookup lookup;

    private NotImplementedApprovalMatrix service;

    @BeforeEach
    void setUp() {
        service = NotImplementedApprovalMatrix.submitOnly(lookup);
    }

    @Test
    @DisplayName("creates one pending request per matrix step when the verdict requires approval")
    void submitForApproval_createsRequestsPerMatrixStep() {
        when(lookup.find(MATRIX_ID, MATRIX_VERSION)).thenReturn(twoStepMatrix());

        SubmitForApprovalResult result = service.submitForApproval(command(PolicyVerdict.REQUIRES_APPROVAL));

        verify(lookup).find(MATRIX_ID, MATRIX_VERSION);
        assertThat(result.requests())
                .extracting(
                        CreatedApprovalRequest::quoteId,
                        CreatedApprovalRequest::approverRole,
                        CreatedApprovalRequest::stepSequence,
                        CreatedApprovalRequest::stepMode,
                        CreatedApprovalRequest::status)
                .containsExactly(
                        tuple(QUOTE_ID, QuoteApprovalTier.DEAL_DESK, 1, StepMode.SERIAL, ApprovalStatus.PENDING),
                        tuple(QUOTE_ID, QuoteApprovalTier.SALES_VP, 2, StepMode.SERIAL, ApprovalStatus.PENDING));
    }

    @Test
    @DisplayName("does not create requests when the verdict is pass")
    void submitForApproval_doesNotCreateRequestsWhenVerdictIsPass() {
        when(lookup.find(MATRIX_ID, MATRIX_VERSION)).thenReturn(twoStepMatrix());

        SubmitForApprovalResult result = service.submitForApproval(command(PolicyVerdict.PASS));

        verify(lookup).find(MATRIX_ID, MATRIX_VERSION);
        assertThat(result.requests()).isEmpty();
        assertThat(result.event()).isNull();
    }

    @Test
    @DisplayName("emits ApprovalRequested for the quote version and the matrix")
    void submitForApproval_emitsApprovalRequested() {
        when(lookup.find(MATRIX_ID, MATRIX_VERSION)).thenReturn(twoStepMatrix());

        SubmitForApprovalResult result = service.submitForApproval(command(PolicyVerdict.REQUIRES_APPROVAL));

        verify(lookup).find(MATRIX_ID, MATRIX_VERSION);
        assertThat(result.event()).isNotNull();
        assertThat(result.event().quoteId()).isEqualTo(QUOTE_ID);
        assertThat(result.event().quoteVersionId()).isEqualTo(QUOTE_VERSION_ID);
        assertThat(result.event().matrixId()).isEqualTo(MATRIX_ID);
        assertThat(result.event().matrixVersion()).isEqualTo(MATRIX_VERSION);
        assertThat(result.event().approvalRequestIds())
                .containsExactlyElementsOf(result.requests().stream().map(CreatedApprovalRequest::id).toList());
    }

    private static SubmitForApproval command(PolicyVerdict verdict) {
        return new SubmitForApproval(
                QUOTE_ID,
                QUOTE_VERSION_ID,
                verdict,
                List.of(new PolicyBreach(
                        ThresholdType.DISCOUNT_PERCENT,
                        new BigDecimal("25"),
                        new BigDecimal("15"),
                        new BigDecimal("25000"))),
                MATRIX_ID,
                MATRIX_VERSION);
    }

    private static ApprovalMatrixView twoStepMatrix() {
        return new ApprovalMatrixView(
                MATRIX_ID,
                MATRIX_VERSION,
                null,
                "discount-serial",
                ThresholdType.DISCOUNT_PERCENT,
                List.of(
                        new MatrixStep(1, StepMode.SERIAL, List.of(QuoteApprovalTier.DEAL_DESK), null, null),
                        new MatrixStep(2, StepMode.SERIAL, List.of(QuoteApprovalTier.SALES_VP), null, null)));
    }
}
