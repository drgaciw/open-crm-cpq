package com.opencrm.rules.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Pricing waterfall fact for tracking pricing adjustments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingWaterfallFact {

    private BigDecimal listPrice;
    private BigDecimal basePrice;

    @Builder.Default
    private List<WaterfallStep> steps = new ArrayList<>();

    private BigDecimal netPrice;
    private String approvalState;

    @Builder.Default
    private List<ExplanationCode> explanationCodes = new ArrayList<>();

    /**
     * Represents a single step in the pricing waterfall.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaterfallStep {
        private String stepType;
        private String description;
        private BigDecimal amount;
        private BigDecimal runningTotal;
        private String ruleId;
    }
}
