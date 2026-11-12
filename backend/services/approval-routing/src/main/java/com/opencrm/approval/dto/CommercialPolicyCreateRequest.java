package com.opencrm.approval.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code DefinePolicy} command payload (BC-04-01, DRG-441): declares a commercial policy version with its scope and
 * thresholds. Leaving {@code segment}/{@code productFamily}/{@code region} blank means "applies to all".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommercialPolicyCreateRequest {

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    private String segment;

    private String productFamily;

    private String region;

    private LocalDate effectiveDate;

    private LocalDate expirationDate;

    /**
     * When set, this definition is a revision: the earlier version with this id is deprecated in the same
     * transaction, and the new policy's version number continues its lineage.
     */
    private Long supersedesPolicyId;

    @NotEmpty(message = "at least one threshold is required")
    @Valid
    private List<PolicyThresholdRequest> thresholds;
}
