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
    private BigDecimal estimatedMarginPct;
}
