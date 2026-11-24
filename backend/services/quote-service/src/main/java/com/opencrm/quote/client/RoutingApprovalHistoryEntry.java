package com.opencrm.quote.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One GetApprovalHistory row from approval-routing. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingApprovalHistoryEntry {

    private Long id;
    private Long approvalId;
    private String quoteId;
    private Long quoteVersion;
    private Long commentId;
    private Long parentCommentId;
    private String kind;
    private String eventName;
    private Instant occurredAt;
    private String actorId;
    private String body;
    private String fromTier;
    private String toTier;
}
