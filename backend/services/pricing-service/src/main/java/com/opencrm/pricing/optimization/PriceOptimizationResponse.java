package com.opencrm.pricing.optimization;

import com.opencrm.pricing.waterfall.PricingResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Advisory optimization result wrapping simulated pricing plus recommendations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceOptimizationResponse {

    private PricingResult simulation;
    private List<AdvisoryPriceElement> recommendations;
    private String modelVersion;
    /** Always true — AI/heuristic guidance is never authoritative. */
    private boolean advisory;
}
