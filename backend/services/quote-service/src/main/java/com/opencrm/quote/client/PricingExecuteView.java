package com.opencrm.quote.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Quote-local view of pricing execute/simulate {@code data} payload.
 * Mirrors fields consumed by {@code QuoteService.calculate}; no pricing-service dep.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingExecuteView {

    private boolean success;
    private String errorMessage;
    private List<PricingLineResultView> lineResults;
    private PricingFinancialMetricsView financialMetrics;
    private String approvalState;
    private String approvalTier;
    /** Opaque waterfall JSON retained for persistence as pricingWaterfallJson. */
    private JsonNode waterfall;
}
