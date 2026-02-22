package com.opencrm.product.configrule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigRuleCreateRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private String description;

    @NotNull(message = "Rule type is required")
    private ConfigRuleType ruleType;

    @NotNull(message = "Source product ID is required")
    private Long sourceProductId;

    private Long targetProductId;

    private String conditionExpression;

    @Size(max = 500, message = "Error message must not exceed 500 characters")
    private String errorMessage;

    private int priority;
}
