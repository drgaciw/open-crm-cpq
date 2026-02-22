package com.opencrm.crm.deal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for pipeline summary statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineSummaryDto {

    private DealStage stage;
    private Long count;
    private BigDecimal totalAmount;
    private BigDecimal weightedAmount;
}
