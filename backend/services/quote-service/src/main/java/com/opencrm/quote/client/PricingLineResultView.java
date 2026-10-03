package com.opencrm.quote.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Quote-local view of a pricing-service line result (no pricing-service Maven dep).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingLineResultView {

    private String productExternalId;
    private String lineItemType;
    private Integer quantity;
    private BigDecimal listPrice;
    private BigDecimal unitPrice;
    private BigDecimal discountRate;
    private BigDecimal discountAmount;
    private BigDecimal finalPrice;
    private List<String> explanationCodes;
    /** Optional period breakdown for this line. Absent on flat quotes. Not required. */
    private JsonNode pricedPeriods;
    /** Optional exit ARR echoed on the line. Absent until pricing emits it. Not required. */
    private BigDecimal exitArr;
}
