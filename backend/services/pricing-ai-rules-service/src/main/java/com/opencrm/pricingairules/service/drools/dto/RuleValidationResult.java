package com.opencrm.pricingairules.service.drools.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of validating rules
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleValidationResult {
    private boolean valid;
    private List<ValidationError> errors;
    private List<ValidationError> warnings;
    private List<ValidationError> infos;
    
    /**
     * Check if there are any errors
     * 
     * @return true if there are errors
     */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
    
    /**
     * Check if there are any warnings
     * 
     * @return true if there are warnings
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
}
