package com.opencrm.rules.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Customer context fact for pricing and renewal rules.
 *
 * <p>The stable fields mirror customer attributes that affect eligibility,
 * discounting, and approval routing. Metadata carries customer-specific policy
 * values that vary by rule set or are still being normalized.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerFact implements MetadataBackedFact {

    private String externalId;
    private String name;
    private String type;
    private String segment;
    private String region;
    private String industry;
    private Integer churnScore;
    private BigDecimal annualRevenue;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
