package com.opencrm.pricingairules.ai.sequential;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a test case for validating a rule
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestCase {
    private String id;
    private String description;
    private Map<String, Object> input;
    private Map<String, Object> expectedOutput;
}
