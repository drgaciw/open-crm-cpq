package com.opencrm.pricing.optimization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Advisory price recommendation element (BC-03-11).
 * Never authoritative — policy evaluation still governs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvisoryPriceElement {

    private String productExternalId;
    private BigDecimal listPrice;
    private BigDecimal recommendedUnitPrice;
    private BigDecimal guidanceFloor;
    private BigDecimal guidanceCeiling;
    private BigDecimal confidence;
    /** Always true for this DTO. */
    private boolean advisory;
    private String explanationCode;
    private Map<String, Object> trace;
}
