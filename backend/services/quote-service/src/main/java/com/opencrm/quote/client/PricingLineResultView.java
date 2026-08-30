package com.opencrm.quote.client;

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
}
