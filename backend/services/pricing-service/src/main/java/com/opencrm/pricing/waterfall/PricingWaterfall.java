package com.opencrm.pricing.waterfall;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Represents the complete pricing waterfall with all calculation steps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingWaterfall {

    private BigDecimal listPrice;
    private BigDecimal basePrice;
    private BigDecimal netPrice;
    private List<PricingWaterfallStep> steps;
    private String approvalState;
    private List<String> explanationCodes;
}
