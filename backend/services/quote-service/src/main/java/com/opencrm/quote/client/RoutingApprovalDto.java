package com.opencrm.quote.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Wire DTO for approval-routing {@code ApprovalRequest} JSON (S2C-12 / G4).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoutingApprovalDto {

    private Long id;
    private String uuid;
    private String quoteId;
    private String approvalType;
    private String status;
    private String requesterId;
    private String approverId;
    private String approvalTier;
    private String reason;
    private String rejectionReason;
    private Instant submittedAt;
    private Instant approvedAt;
    private Instant rejectedAt;
    /** The request that replaced this one, when its status is SUPERSEDED. */
    private Long supersededBy;
    private Instant supersededAt;
    private Instant createdAt;
}
