package com.opencrm.approval.verdict;

import java.math.BigDecimal;

/**
 * One row of {@code ExplainPolicyBreach}: the threshold that failed, the actual value, the limit, and the deal
 * amount the approval matrix uses to pick a band. {@code breachType} uses the DRG-441 {@code ThresholdType} names.
 *
 * <p>Stand-in until DRG-442's explanation type lands on this branch.
 */
public record PolicyBreach(
        String breachType,
        BigDecimal actual,
        BigDecimal limit,
        BigDecimal amount,
        String clauseCode) {
}
