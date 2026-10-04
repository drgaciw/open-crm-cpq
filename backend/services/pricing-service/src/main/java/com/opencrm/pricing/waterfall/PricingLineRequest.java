package com.opencrm.pricing.waterfall;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.common.pricing.RampStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
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

    /** Explicit ramp periods. Absent on a flat line. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<RampStep> schedule;

    /**
     * The line's billing frequency. Only read when {@link #schedule} is present: a schedule
     * needs a recurring frequency (ADR-023 D-01, REQ FR-01/FR-02). Absent on today's flat calls.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String billingFrequency;
}
