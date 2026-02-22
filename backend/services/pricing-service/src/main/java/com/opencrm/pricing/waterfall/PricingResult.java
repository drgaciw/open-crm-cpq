package com.opencrm.pricing.waterfall;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Complete result of pricing execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingResult {

    private boolean success;
    private List<PricingLineResult> lineResults;
    private PricingWaterfall waterfall;
    private FinancialMetrics financialMetrics;
    private List<String> explanationCodes;
    private String approvalState;
    private String approvalTier;
    private Long executionTimeMs;
    private String errorMessage;
}
