package com.opencrm.approval.dto;

import com.opencrm.approval.domain.QuoteApprovalTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Create/submit payload for quote BFF → approval-routing (S2C-12 / Wave G3).
 * Internal to agents; agent-facing URLs remain under {@code /api/v1/quotes/{id}/approvals}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalSubmitRequest {

    @NotBlank(message = "quoteId is required")
    private String quoteId;

    @NotNull(message = "tier is required")
    private QuoteApprovalTier tier;

    /** Defaults to {@code system} when omitted. */
    private String requesterId;

    private String lineId;

    private String reason;

    private BigDecimal requestedDiscount;

    private BigDecimal marginDelta;

    private String currencyCode;
}
