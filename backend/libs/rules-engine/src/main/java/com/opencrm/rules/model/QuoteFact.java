package com.opencrm.rules.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Quote-level pricing fact for Drools rule evaluation.
 *
 * <p>Canonical fields capture shared quote context used across pricing, approval,
 * renewal, and compatibility rules. Metadata is reserved for transitional or
 * rule-specific values such as derived margins and approval routing markers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteFact implements MetadataBackedFact {

    private String externalId;
    private String subscriptionType;
    private int durationInMonths;
    private BigDecimal basePrice;
    private BigDecimal finalPrice;
    private String currency;
    private CustomerFact customer;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

}
