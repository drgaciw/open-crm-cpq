package com.opencrm.pricingairules.ai.sequential;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single step in a sequential thinking process
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThinkingStep {
    private int stepNumber;
    private int totalSteps;
    private String thought;
}
