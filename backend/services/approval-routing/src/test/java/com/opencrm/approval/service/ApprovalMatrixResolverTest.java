package com.opencrm.approval.service;

import com.opencrm.approval.domain.MatrixStepMode;
import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Approval matrix resolution")
class ApprovalMatrixResolverTest {

    @Test
    @DisplayName("serial steps stay ordered and a later step is distinct from the first")
    void serialStepsStayOrdered() {
        ApprovalMatrixResolver.ResolvedRoute route = ApprovalMatrixResolver.resolve(9L, 1, List.of(
                rule("DISCOUNT_PERCENT", "0", "100000", 1, MatrixStepMode.SERIAL, QuoteApprovalTier.DEAL_DESK),
                rule("DISCOUNT_PERCENT", "0", "100000", 2, MatrixStepMode.SERIAL, QuoteApprovalTier.SALES_VP)),
                List.of(breach("DISCOUNT_PERCENT", "25000")));

        assertThat(route.matrixVersion()).isEqualTo(1);
        assertThat(route.steps()).extracting(ApprovalMatrixResolver.ResolvedStep::order).containsExactly(1, 2);
        assertThat(route.steps().get(0).roles()).containsExactly(QuoteApprovalTier.DEAL_DESK);
        assertThat(route.steps().get(1).mode()).isEqualTo(MatrixStepMode.SERIAL);
        assertThat(route.steps().get(1).roles()).containsExactly(QuoteApprovalTier.SALES_VP);
    }

    @Test
    @DisplayName("a parallel step collects every role in that step")
    void parallelStepCollectsRoles() {
        ApprovalMatrixResolver.ResolvedRoute route = ApprovalMatrixResolver.resolve(9L, 3, List.of(
                rule("MARGIN_FLOOR", null, "50000", 1, MatrixStepMode.PARALLEL, QuoteApprovalTier.DEAL_DESK),
                rule("MARGIN_FLOOR", null, "50000", 1, MatrixStepMode.PARALLEL, QuoteApprovalTier.SALES_VP)),
                List.of(breach("MARGIN_FLOOR", "1000")));

        assertThat(route.matrixVersion()).isEqualTo(3);
        assertThat(route.steps()).hasSize(1);
        assertThat(route.steps().get(0).mode()).isEqualTo(MatrixStepMode.PARALLEL);
        assertThat(route.steps().get(0).roles())
                .containsExactly(QuoteApprovalTier.DEAL_DESK, QuoteApprovalTier.SALES_VP);
    }

    @Test
    @DisplayName("a higher amount band selects that version's role, not the lower band")
    void amountBandSelectsTheMatchingVersion() {
        List<ApprovalMatrixResolver.Rule> versionOne = List.of(
                rule("DISCOUNT_PERCENT", "0", "10000", 1, MatrixStepMode.SERIAL, QuoteApprovalTier.DEAL_DESK));
        List<ApprovalMatrixResolver.Rule> versionTwo = List.of(
                rule("DISCOUNT_PERCENT", "0", "10000", 1, MatrixStepMode.SERIAL, QuoteApprovalTier.DEAL_DESK),
                rule("DISCOUNT_PERCENT", "10000", null, 1, MatrixStepMode.SERIAL, QuoteApprovalTier.CFO));

        assertThat(ApprovalMatrixResolver.resolve(1L, 1, versionOne, List.of(breach("DISCOUNT_PERCENT", "500")))
                .steps().get(0).roles()).containsExactly(QuoteApprovalTier.DEAL_DESK);
        assertThat(ApprovalMatrixResolver.resolve(2L, 2, versionTwo, List.of(breach("DISCOUNT_PERCENT", "50000")))
                .steps().get(0).roles()).containsExactly(QuoteApprovalTier.CFO);
        assertThat(ApprovalMatrixResolver.resolve(2L, 2, versionTwo, List.of(breach("DISCOUNT_PERCENT", "10000")))
                .steps().get(0).roles()).containsExactly(QuoteApprovalTier.CFO);
    }

    @Test
    @DisplayName("breaches from two types merge onto the same step order")
    void breachesMergeByStep() {
        ApprovalMatrixResolver.ResolvedRoute route = ApprovalMatrixResolver.resolve(4L, 1, List.of(
                rule("DISCOUNT_PERCENT", null, null, 1, MatrixStepMode.PARALLEL, QuoteApprovalTier.DEAL_DESK),
                rule("TERM_MONTHS", null, null, 1, MatrixStepMode.PARALLEL, QuoteApprovalTier.SALES_VP)),
                List.of(breach("DISCOUNT_PERCENT", "10"), breach("TERM_MONTHS", "10")));

        assertThat(route.steps()).hasSize(1);
        assertThat(route.steps().get(0).roles())
                .containsExactly(QuoteApprovalTier.DEAL_DESK, QuoteApprovalTier.SALES_VP);
        assertThat(route.steps().get(0).breachTypes()).containsExactly("DISCOUNT_PERCENT", "TERM_MONTHS");
    }

    @Test
    @DisplayName("an unrouted breach and a serial step with two roles fail closed")
    void failClosed() {
        List<ApprovalMatrixResolver.Rule> rules = List.of(
                rule("DISCOUNT_PERCENT", "0", "10", 1, MatrixStepMode.SERIAL, QuoteApprovalTier.DEAL_DESK));
        assertThatThrownBy(() -> ApprovalMatrixResolver.resolve(1L, 1, rules, List.of(breach("DISCOUNT_PERCENT", "11"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("UNROUTED_BREACH");

        List<ApprovalMatrixResolver.Rule> conflict = List.of(
                rule("DISCOUNT_PERCENT", null, null, 1, MatrixStepMode.SERIAL, QuoteApprovalTier.DEAL_DESK),
                rule("MARGIN_FLOOR", null, null, 1, MatrixStepMode.SERIAL, QuoteApprovalTier.CFO));
        assertThatThrownBy(() -> ApprovalMatrixResolver.resolve(1L, 1, conflict,
                List.of(breach("DISCOUNT_PERCENT", "1"), breach("MARGIN_FLOOR", "1"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("MATRIX_STEP_CONFLICT");
    }

    private static ApprovalMatrixResolver.Rule rule(String type, String min, String max, int order,
                                                    MatrixStepMode mode, QuoteApprovalTier role) {
        return new ApprovalMatrixResolver.Rule(type,
                min == null ? null : new BigDecimal(min),
                max == null ? null : new BigDecimal(max),
                order, mode, role);
    }

    private static ApprovalMatrixResolver.RoutedBreach breach(String type, String amount) {
        return new ApprovalMatrixResolver.RoutedBreach(type, new BigDecimal(amount));
    }
}
