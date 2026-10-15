package com.opencrm.pricing.waterfall;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Financial metrics calculated from pricing results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialMetrics {

    private BigDecimal arr;  // Annual Recurring Revenue
    private BigDecimal tcv;  // Total Contract Value
    private BigDecimal acv;  // Annual Contract Value

    /** Final-period run rate × 12. Omitted on a flat quote. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal exitArr;
    private BigDecimal clv;  // Customer Lifetime Value
    private Integer churnScore;
    private BigDecimal discountRate;
    private BigDecimal totalDiscount;
    private BigDecimal totalSurcharge;
    private BigDecimal priceMultiplier;

    /**
     * Margin as a PERCENT (40.0 = 40%). Under {@code opencrm.pricing.margin.source=placeholder} (the default) it is
     * the non-authoritative constant 40.0, which no policy may read (ADR-023 D-06). Under {@code cost-basis} it is
     * {@code marginFraction × 100}, or null when a cost is missing; never 40.0.
     */
    private BigDecimal estimatedMarginPct;

    /**
     * Cost-basis margin as a FRACTION (0.35 = 35%), {@code (net − unit cost) / net} over the quote, weighted like
     * TCV (finance decision F-1). Present only under {@code cost-basis}, and absent when any line has no usable cost.
     * This is the value margin policy compares with the 0.35 floor.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal marginFraction;

    /** {@code cost-basis} when margin came from the cost basis. Omitted under the placeholder, as today. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String marginSource;
}
