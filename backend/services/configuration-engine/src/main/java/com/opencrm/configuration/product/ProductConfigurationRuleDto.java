package com.opencrm.configuration.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Subset of product-service {@code ConfigurationRuleDto} for CE evaluation
 * (Wave F REQUIRES/EXCLUDES + Wave G S2B-8 extended types).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductConfigurationRuleDto {

    private Long id;
    private String name;
    private String description;
    private String ruleType;
    private Long sourceProductId;
    private Long targetProductId;
    /** Product-service VALIDATES/DEFAULT expression payload (Wave G). */
    private String conditionExpression;
    private String errorMessage;
    private int priority;
}
