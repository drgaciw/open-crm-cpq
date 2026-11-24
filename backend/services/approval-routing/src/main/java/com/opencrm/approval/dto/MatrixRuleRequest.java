package com.opencrm.approval.dto;

import com.opencrm.approval.domain.MatrixStepMode;
import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.approval.domain.ThresholdType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One matrix rule: breach type and amount band {@code [amountMin, amountMax)} map to an existing approver tier.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatrixRuleRequest {

    @NotNull
    private ThresholdType breachType;

    private BigDecimal amountMin;

    private BigDecimal amountMax;

    @NotNull
    @Min(1)
    private Integer stepOrder;

    @NotNull
    private MatrixStepMode stepMode;

    @NotNull
    private QuoteApprovalTier approverRole;
}
