package com.opencrm.rules.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Customer fact for rules engine evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerFact {

    private String externalId;
    private String name;
    private String type;
    private String segment;
    private String region;
    private String industry;
    private Integer churnScore;
    private BigDecimal annualRevenue;

    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
}
