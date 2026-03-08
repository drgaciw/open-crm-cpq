package com.opencrm.dealdesk.rules.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class RuleDefinitionRequestDto {

    @NotBlank(message = "ruleKey is required")
    @Size(max = 255, message = "ruleKey must be <= 255 characters")
    private String ruleKey;

    @Size(max = 255, message = "name must be <= 255 characters")
    private String name;

    private String description;

    @Size(max = 100, message = "category must be <= 100 characters")
    private String category;

    @Min(value = 0, message = "priority must be >= 0")
    @Max(value = 10000, message = "priority must be <= 10000")
    private Integer priority;

    @NotBlank(message = "conditionExpression is required")
    private String conditionExpression;

    private String actionExpression;

    private Boolean enabled;

    private Map<String, Object> parameters = new LinkedHashMap<>();
}
