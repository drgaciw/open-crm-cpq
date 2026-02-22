package com.opencrm.pricing.waterfall;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request for a single line item in pricing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingLineRequest {

    private String productExternalId;
    private String lineItemType;
    private Integer quantity;
    private BigDecimal listPriceOverride;
    private Map<String, Object> metadata;
}
