package com.opencrm.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Wire form of one {@code ExplainPolicyBreach} row supplied with {@code SubmitForApproval}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyBreachRequest {

    @NotBlank
    private String breachType;

    private BigDecimal actual;

    private BigDecimal limit;

    @NotNull
    private BigDecimal amount;

    private String clauseCode;
}
