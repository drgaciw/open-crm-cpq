package com.opencrm.product.configrule;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigurationRuleDto {

    private Long id;
    private String uuid;
    private String name;
    private String description;
    private ConfigRuleType ruleType;
    private Long sourceProductId;
    private Long targetProductId;
    private String conditionExpression;
    private String errorMessage;
    private int priority;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
