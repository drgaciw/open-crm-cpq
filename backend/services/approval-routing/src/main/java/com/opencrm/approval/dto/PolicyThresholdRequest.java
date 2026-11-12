package com.opencrm.approval.dto;

import com.opencrm.approval.domain.ThresholdType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One threshold in a {@code DefinePolicy} request. Which of {@link #limitValue} / {@link #clauseCode} is required
 * depends on {@link #thresholdType}; see {@code CommercialPolicyService} for the per-type validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyThresholdRequest {

    @NotNull(message = "thresholdType is required")
    private ThresholdType thresholdType;

    private BigDecimal limitValue;

    private String clauseCode;

    private String description;
}
