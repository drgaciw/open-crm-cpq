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
}
