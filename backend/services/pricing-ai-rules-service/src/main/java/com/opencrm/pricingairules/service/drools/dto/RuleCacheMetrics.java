package com.opencrm.pricingairules.service.drools.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cache metrics for a rule set
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleCacheMetrics {
    private Long size;
    private Long capacity;
    private Double hitRate;
    private Double missRate;
    private Long evictions;
    private Long loadTime;
    private String evictionPolicy;
    private Long ttl;
}
