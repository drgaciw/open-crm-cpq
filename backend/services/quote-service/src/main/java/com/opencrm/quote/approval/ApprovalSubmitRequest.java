package com.opencrm.quote.approval;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Submit a quote for approval. The requester is the authenticated user, never a field (ADR-016 decision 6). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalSubmitRequest {

    @NotNull(message = "Approval tier is required")
    private ApprovalTier tier;
}
