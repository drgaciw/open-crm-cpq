package com.opencrm.pricing.waterfall;

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
    private BigDecimal clv;  // Customer Lifetime Value
    private BigDecimal totalDiscount;
    private BigDecimal totalSurcharge;
    private BigDecimal estimatedMarginPct;
}
