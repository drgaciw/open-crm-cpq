package com.opencrm.dealdesk.rules.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

@Value
@Builder
public class RuleDefinitionResponseDto {
    Long id;
    String uuid;
    String ruleKey;
    String name;
    String description;
    String category;
    Integer priority;
    String conditionExpression;
    String actionExpression;
    boolean enabled;
    Map<String, Object> parameters;
    Instant createdAt;
    Instant updatedAt;
}
