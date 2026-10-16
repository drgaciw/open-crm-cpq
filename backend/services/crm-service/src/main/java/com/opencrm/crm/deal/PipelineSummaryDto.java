package com.opencrm.crm.deal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for pipeline summary statistics. Amounts are in one base currency (ADR-032).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineSummaryDto {

    private DealStage stage;
    /** All non-deleted deals in the stage, converted or not. */
    private Long count;
    /** Sum of base amounts, in {@link #baseCurrency}. Excludes unconverted deals (ADR-032 D-04). */
    private BigDecimal totalAmount;
    /** Sum of probability-weighted base amounts, in {@link #baseCurrency}. */
    private BigDecimal weightedAmount;
    /** Currency of {@link #totalAmount} and {@link #weightedAmount}. */
    private String baseCurrency;
    /** Deals with an amount that are not in the totals because no rate was available. */
    private Long unconvertedCount;
}
