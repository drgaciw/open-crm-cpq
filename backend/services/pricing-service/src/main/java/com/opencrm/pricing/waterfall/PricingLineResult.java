package com.opencrm.pricing.waterfall;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Result for a single line item after pricing execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingLineResult {

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
}
