package com.opencrm.quote.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Quote-local view of pricing financial metrics used by calculate apply.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingFinancialMetricsView {

    private BigDecimal arr;
    private BigDecimal tcv;
    private BigDecimal acv;
    /** Optional exit ARR. Absent until pricing emits it. Not required. */
    private BigDecimal exitArr;
    private BigDecimal clv;
    private Integer churnScore;
    private BigDecimal discountRate;
    private BigDecimal totalDiscount;
    private BigDecimal totalSurcharge;
    private BigDecimal priceMultiplier;
    private BigDecimal estimatedMarginPct;
}
