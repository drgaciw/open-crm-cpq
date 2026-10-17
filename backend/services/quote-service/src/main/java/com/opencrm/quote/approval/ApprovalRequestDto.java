package com.opencrm.quote.approval;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApprovalRequestDto {

    private Long id;
    private String uuid;
    private Long quoteId;
    private ApprovalTier tier;
    private ApprovalStatus status;
    private String requestedBy;
    private String approver;
    private String comments;
    private String pricingFingerprint;
    private Integer governingPeriod;
    private java.math.BigDecimal requestedDiscount;
    private String reason;
    private Instant requestedAt;
    private Instant respondedAt;
    /** Set when {@code status} is SUPERSEDED: the approval that replaced this one. */
    private Long supersededBy;
    private Instant createdAt;
}
