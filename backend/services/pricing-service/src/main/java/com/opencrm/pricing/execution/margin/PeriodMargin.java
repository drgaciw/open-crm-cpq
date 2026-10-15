package com.opencrm.pricing.execution.margin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Margin of one ramp period: monthly net against unit cost × the period's quantity. */
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
    /** Net per month for the period. */
    private BigDecimal net;
    /** Unit cost × quantity, per month. Absent when no usable cost exists. */
    private BigDecimal cost;
    /** {@code (net − cost) / net}, a fraction. Absent when the cost is missing or the net is not positive. */
    private BigDecimal marginFraction;
    private CostEvidence unitCost;
    /** Why there is no margin, one of the {@link CostBasisMarginEvaluator} reason codes. */
    private String missingCostReason;
}
