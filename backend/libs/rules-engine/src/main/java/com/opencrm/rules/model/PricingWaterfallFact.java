package com.opencrm.rules.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pricing waterfall fact for tracking ordered pricing adjustments.
 *
 * <p>Rules append steps and explanation codes as pricing changes are applied.
 * Metadata is reserved for execution-stage markers that should not become part
 * of the persisted waterfall contract until they are stable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingWaterfallFact implements MetadataBackedFact {

    private BigDecimal listPrice;
    private BigDecimal basePrice;

    @Builder.Default
    private List<WaterfallStep> steps = new ArrayList<>();

    private BigDecimal netPrice;
    private String approvalState;

    @Builder.Default
    private List<ExplanationCode> explanationCodes = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

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
