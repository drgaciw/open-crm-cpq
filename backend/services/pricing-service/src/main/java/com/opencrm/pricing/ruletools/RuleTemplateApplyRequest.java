package com.opencrm.pricing.ruletools;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Apply a DRL template into a DRAFT rule (PD1-2). Never activates the rule set.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleTemplateApplyRequest {

    @NotNull(message = "Rule set ID is required")
    private Long ruleSetId;

    @Size(max = 255)
    private String name;

    private String description;

    @Min(100)
    @Max(1000)
    private Integer priority;

    @Builder.Default
    private Map<String, Object> parameters = new LinkedHashMap<>();
}
