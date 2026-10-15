package com.opencrm.pricing.execution.margin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Margin of one quote line. A flat line carries its own cost evidence. A ramp line carries one
 * {@link PeriodMargin} per period, and its own figures are the term totals over those periods.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LineMargin {

    private Integer lineIndex;
    private String productExternalId;
    private String source;
    private String costAsOf;
    private BigDecimal net;
    private BigDecimal cost;
    private BigDecimal marginFraction;
    private CostEvidence unitCost;
    private String missingCostReason;
    private List<PeriodMargin> periods;
}
