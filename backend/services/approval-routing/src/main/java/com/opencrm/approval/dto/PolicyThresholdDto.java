package com.opencrm.approval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.approval.domain.ThresholdType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * API-boundary DTO for {@code PolicyThreshold}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolicyThresholdDto {

    private Long id;
    private ThresholdType thresholdType;
    private BigDecimal limitValue;
    private String clauseCode;
    private String description;
}
