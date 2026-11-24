package com.opencrm.approval.dto;

import com.opencrm.approval.domain.MatrixStepMode;
import com.opencrm.approval.domain.QuoteApprovalTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** One matrix step after rules that share a sequence, mode, and amount band are grouped. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatrixStepDto {

    private Integer sequence;
    private MatrixStepMode mode;
    @Builder.Default
    private List<QuoteApprovalTier> roles = new ArrayList<>();
    private BigDecimal minAmountInclusive;
    private BigDecimal maxAmountExclusive;
}
