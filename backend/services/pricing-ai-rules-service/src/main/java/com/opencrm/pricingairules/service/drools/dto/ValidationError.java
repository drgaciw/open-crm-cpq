package com.opencrm.pricingairules.service.drools.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an error or warning in rule validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {
    private String code;
    private String message;
    private ErrorSeverity severity;
    private Integer line;
    private Integer column;
    
    /**
     * Severity of the validation error
     */
    public enum ErrorSeverity {
        ERROR,
        WARNING,
        INFO
    }
}
