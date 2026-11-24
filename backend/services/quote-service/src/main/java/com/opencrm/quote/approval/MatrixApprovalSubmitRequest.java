package com.opencrm.quote.approval;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Quote-BFF body for {@code SubmitForApproval}. Tenant and quote version come from the quote row.
 * The verdict is the DRG-442 stand-in: PASS, REQUIRES_APPROVAL, or BLOCKED.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatrixApprovalSubmitRequest {

    @NotNull
    private String verdict;

    private Long matrixId;

    @Valid
    private List<Breach> breaches;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Breach {
        @NotNull
        private String breachType;
        private BigDecimal actual;
        private BigDecimal limit;
        @NotNull
        private BigDecimal amount;
        private String clauseCode;
    }
}
