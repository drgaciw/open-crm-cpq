package com.opencrm.pricing.execution;

import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingResult;

import java.time.Instant;

/**
 * Domain-facing audit record for a single pricing invocation.
 * {@link PricingAuditLog} is the persistence representation.
 */
public record PriceCalculation(
        String correlationId,
        String quoteExternalId,
        String customerExternalId,
        String pricebookVersion,
        String rulesetVersion,
        FinancialMetrics metrics,
        PricingResult result,
        Instant executedAt,
        long executionTimeMs
) {
}
