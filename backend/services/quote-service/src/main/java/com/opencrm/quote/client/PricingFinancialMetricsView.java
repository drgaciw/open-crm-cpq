package com.opencrm.quote.client;

import com.fasterxml.jackson.databind.JsonNode;
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
    /** Optional period breakdown. Absent on flat quotes. Not required. */
    private JsonNode pricedPeriods;
    /** Optional exit ARR. Absent until pricing emits it. Not required. */
    private BigDecimal exitArr;
}
