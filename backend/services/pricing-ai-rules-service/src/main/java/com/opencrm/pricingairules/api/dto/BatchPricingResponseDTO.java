package com.opencrm.pricingairules.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchPricingResponseDTO {
    private List<QuoteDTO> quotes;
    private String ruleSetName;
    private Integer processedCount;
    private Integer successCount;
    private Integer failureCount;
    private Boolean success;
    private String message;
}
