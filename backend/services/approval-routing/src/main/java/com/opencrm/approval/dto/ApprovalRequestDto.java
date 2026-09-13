package com.opencrm.approval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * API-boundary DTO for approval-routing {@code ApprovalRequest}.
 * Wire fields required by quote-service {@code RoutingApprovalDto} are always present;
 * entity {@code deleted} is intentionally omitted.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApprovalRequestDto {

    private Long id;
    private String uuid;
    /** String quote id — matches routing entity / RoutingApprovalDto (not Long). */
    private String quoteId;
    private String lineId;
    private ApprovalType approvalType;
    private ApprovalStatus status;
    private BigDecimal requestedDiscount;
    private BigDecimal marginDelta;
    private String currencyCode;
    private String requesterId;
    private String approverId;
    private String approvalTier;
    private String reason;
    private String rejectionReason;
    private Instant submittedAt;
    private Instant approvedAt;
    private Instant rejectedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
