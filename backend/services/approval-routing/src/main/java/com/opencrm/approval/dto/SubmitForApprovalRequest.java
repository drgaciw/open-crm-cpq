package com.opencrm.approval.dto;

import com.opencrm.approval.verdict.PolicyVerdict;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code SubmitForApproval}. The requester is the caller. {@code quoteTenantId} is the quote row's tenant, copied by
 * the quote BFF from the quote, and must match the caller's {@code tid}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitForApprovalRequest {

    @NotBlank
    private String quoteId;

    @NotBlank
    private String quoteVersion;

    @NotNull
    private PolicyVerdict verdict;

    @NotBlank
    private String quoteTenantId;

    /** Active matrix to bind. When null, the tenant's single active matrix is used. */
    private Long matrixId;

    @Valid
    private List<PolicyBreachRequest> breaches;
}
