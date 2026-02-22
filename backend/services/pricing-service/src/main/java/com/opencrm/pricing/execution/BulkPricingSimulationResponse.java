package com.opencrm.pricing.execution;

import com.opencrm.pricing.waterfall.PricingResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response payload for bulk pricing simulation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkPricingSimulationResponse {

    private int totalRequests;
    private int successfulRequests;
    private int failedRequests;
    private Long totalExecutionTimeMs;
    private List<PricingResult> results;
}
