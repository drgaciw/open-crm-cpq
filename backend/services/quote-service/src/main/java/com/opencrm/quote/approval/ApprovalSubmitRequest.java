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

    /** Version the caller intends to submit; stale commands are refused before routing. */
    @NotNull(message = "Quote version is required")
    @jakarta.validation.constraints.PositiveOrZero
    private Long expectedVersion;
}
