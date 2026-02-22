package com.opencrm.pricingairules.service.drools.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of deploying rules to the rule engine
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDeploymentResult {
    private String id;
    private DeploymentStatus status;
    private String message;
    private List<ValidationError> validationErrors;
    
    /**
     * Status of the rule deployment
     */
    public enum DeploymentStatus {
        DEPLOYED,
        PENDING,
        FAILED
    }
}
