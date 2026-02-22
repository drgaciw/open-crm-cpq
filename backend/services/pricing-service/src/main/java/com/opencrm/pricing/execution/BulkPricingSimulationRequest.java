package com.opencrm.pricing.execution;

import com.opencrm.pricing.waterfall.PricingContext;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request payload for bulk pricing simulation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkPricingSimulationRequest {

    @NotEmpty(message = "At least one pricing context is required")
    private List<PricingContext> contexts;
}
