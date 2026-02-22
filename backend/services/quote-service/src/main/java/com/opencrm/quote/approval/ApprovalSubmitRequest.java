package com.opencrm.quote.approval;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalSubmitRequest {

    @NotNull(message = "Approval tier is required")
    private ApprovalTier tier;

    private String requestedBy;
}
