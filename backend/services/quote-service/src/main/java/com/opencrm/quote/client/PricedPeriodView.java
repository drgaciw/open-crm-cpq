package com.opencrm.quote.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * One priced ramp period on a quote pricing line. All fields are optional.
 * {@code discountRate} is a fraction against list, the same meaning as the line
 * {@code discountRate}. {@code waterfall} stays opaque JSON.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricedPeriodView {

    private Integer sequence;
    private Integer startMonth;
    private Integer months;
    private Integer quantity;
    private BigDecimal netUnitPrice;
    private BigDecimal netMonthly;
    private BigDecimal periodAmount;
    private BigDecimal discountRate;
    private List<String> explanationCodes;
    private JsonNode waterfall;
}
