package com.opencrm.configuration.model;

public record ConfigurationRuleSnapshot(
        Long id,
        String ruleType,
        Long sourceProductId,
        Long targetProductId,
        int priority,
        String expression,
        String errorMessage
) {
}
