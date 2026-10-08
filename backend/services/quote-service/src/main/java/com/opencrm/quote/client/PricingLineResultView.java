package com.opencrm.quote.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
    private BigDecimal surchargeAmount;
    private BigDecimal finalPrice;
    private List<String> explanationCodes;
    private Map<String, Object> metadata;
    /** Optional priced periods. Absent on flat quotes. Not required. */
    private List<PricedPeriodView> pricedPeriods;
}
