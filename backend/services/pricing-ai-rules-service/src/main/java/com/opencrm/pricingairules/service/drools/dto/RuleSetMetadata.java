package com.opencrm.pricingairules.service.drools.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Metadata about a rule set
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleSetMetadata {
    private String id;
    private String name;
    private String version;
    private RuleSetStatus status;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private String author;
    private Long executionCount;
    private Long ruleCount;
    private RuleValidationResult validationStatus;
    
    /**
     * Status of the rule set
     */
    public enum RuleSetStatus {
        ACTIVE,
        INACTIVE,
        DELETED
    }
}
