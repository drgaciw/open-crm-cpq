package com.opencrm.pricing.waterfall;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents a single step in the pricing waterfall.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingWaterfallStep {

    private WaterfallStepType stepType;
    private String description;
    private BigDecimal amount;
    private BigDecimal runningTotal;
    private String ruleId;
    private String ruleName;
}
