package com.opencrm.pricing.execution.margin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Margin of one ramp period: the period amount (after any hybrid migration credit) against unit cost × quantity ×
 * months (review finding M2).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PeriodMargin {

    private Integer sequence;
    /** The period's start date, the date the cost was looked up on (F-1 effective dating). */
    private String costAsOf;
    private Integer quantity;
    private Integer months;
    /** Net for the whole period: its {@code periodAmount}, which a hybrid migration credit lowers. */
    private BigDecimal net;
    /** The hybrid migration credit already deducted from {@code net}, when the period carries one. */
    private BigDecimal migrationCredit;
    /** Unit cost × quantity × months. Absent when no usable cost exists. */
    private BigDecimal cost;
    /** {@code (net − cost) / net}, a fraction. Absent when the cost is missing or the net is not positive. */
    private BigDecimal marginFraction;
    private CostEvidence unitCost;
    /** Why there is no margin, one of the {@link CostBasisMarginEvaluator} reason codes. */
    private String missingCostReason;
    /** {@code MARGIN_NOT_APPLICABLE_ZERO_NET}: the period net is exactly zero; its cost still counts in the totals. */
    private String notApplicableReason;
}
