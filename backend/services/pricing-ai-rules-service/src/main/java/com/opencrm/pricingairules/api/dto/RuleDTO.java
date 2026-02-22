package com.opencrm.pricingairules.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleDTO {
    private Long id;
    private String name;
    private String description;
    private String ruleType;
    private String content;
    private Integer priority;
    private String status;
}
