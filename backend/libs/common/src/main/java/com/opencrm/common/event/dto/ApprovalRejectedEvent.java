package com.opencrm.common.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when an approval is rejected.
 * Topic: cpq.quote.approval.rejected
 * Publisher: Approval Routing
 * Subscribers: Quote Management, Pricing Engine
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRejectedEvent {
    private String quoteId;
    private Instant rejectedAt;
    private String rejectionReason;
    private String nextAction;
}
