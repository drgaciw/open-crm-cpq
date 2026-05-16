package com.opencrm.pricing.waterfall;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Context for pricing execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingContext {

    private String correlationId;
    private String customerExternalId;
    private String quoteExternalId;
    private List<PricingLineRequest> lineItems;
    private String region;
    private String currency;
    private Integer durationInMonths;
    private String subscriptionType; // NEW, AMENDMENT, RENEWAL
    private Map<String, Object> metadata;
}
