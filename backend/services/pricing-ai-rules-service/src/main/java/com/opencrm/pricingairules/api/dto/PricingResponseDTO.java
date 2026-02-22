package com.opencrm.pricingairules.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PricingResponseDTO {
    private QuoteDTO quote;
    private String ruleSetName;
    private Map<String, Object> financialMetrics;
    private Map<String, Object> pricingDetails;
    private Boolean success;
    private String message;
}
