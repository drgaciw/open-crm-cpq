package com.opencrm.product.configrule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Editable rule fields. The source product remains unchanged. */
public record ConfigRuleUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotNull ConfigRuleType ruleType,
        @Positive Long targetProductId,
        String conditionExpression,
        @Size(max = 500) String errorMessage,
        @NotNull Integer priority,
        @NotNull ConfigRuleStatus status) {
}
