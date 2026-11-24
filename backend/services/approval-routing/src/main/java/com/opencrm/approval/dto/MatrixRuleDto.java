package com.opencrm.approval.dto;

import com.opencrm.approval.domain.MatrixStepMode;
import com.opencrm.approval.domain.QuoteApprovalTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatrixRuleDto {

    private Long id;
    private String breachType;
    private BigDecimal amountMin;
    private BigDecimal amountMax;
    private Integer stepOrder;
    private MatrixStepMode stepMode;
    private QuoteApprovalTier approverRole;
}
