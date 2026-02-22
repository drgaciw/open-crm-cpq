package com.opencrm.pricing.rule;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for Rule entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleDto {

    private Long id;
    private String uuid;
    private Long ruleSetId;
    private String name;
    private String description;
    private RuleType ruleType;
    private Integer priority;
    private RuleStatus status;
    private String content;
    private String conditionGroup;
    private String actionSet;
    private String conflictPolicy;
    private String explanationCode;
    private String metadata;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
