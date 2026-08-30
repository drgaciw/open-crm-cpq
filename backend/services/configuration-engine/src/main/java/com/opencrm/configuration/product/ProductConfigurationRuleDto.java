package com.opencrm.configuration.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Subset of product-service {@code ConfigurationRuleDto} used for thin
 * REQUIRES/EXCLUDES parity evaluation (Wave F / S2B-11).
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
    private String errorMessage;
    private int priority;
}
