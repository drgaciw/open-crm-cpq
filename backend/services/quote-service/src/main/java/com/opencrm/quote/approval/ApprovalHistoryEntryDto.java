package com.opencrm.quote.approval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One GetApprovalHistory entry exposed by the quote BFF.
 * {@code quoteVersion} links the quote. {@code commentId} and {@code parentCommentId} link the thread.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalHistoryEntryDto {

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
