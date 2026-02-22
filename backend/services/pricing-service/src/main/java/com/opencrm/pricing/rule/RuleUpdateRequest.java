package com.opencrm.pricing.rule;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleUpdateRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private String description;

    private RuleType ruleType;

    @Min(value = 100, message = "Priority must be at least 100")
    @Max(value = 1000, message = "Priority must not exceed 1000")
    private Integer priority;

    private String content;

    private String conditionGroup;

    private String actionSet;

    @Size(max = 50, message = "Conflict policy must not exceed 50 characters")
    private String conflictPolicy;

    @Size(max = 100, message = "Explanation code must not exceed 100 characters")
    private String explanationCode;

    private String metadata;
}
