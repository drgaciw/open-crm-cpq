package com.opencrm.approval.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code DefineApprovalMatrix}. The tenant is the caller's {@code tid}, never a field on this body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefineApprovalMatrixRequest {

    @NotBlank
    private String name;

    /** When set, this definition revises that version and deprecates it in the same transaction. */
    private Long supersedesMatrixId;

    @NotEmpty
    @Valid
    private List<MatrixRuleRequest> rules;
}
