package com.opencrm.approval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.approval.domain.ApprovalTimelineKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One GetApprovalHistory entry. {@code quoteVersion} links the quote version.
 * {@code commentId} links a case comment. {@code parentCommentId} links a thread reply to its parent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApprovalHistoryEntryDto {

    private Long id;
    private Long approvalId;
    private String quoteId;
    private Long quoteVersion;
    private Long commentId;
    private Long parentCommentId;
    private ApprovalTimelineKind kind;
    /** ApprovalGranted, ApprovalRejected, ApprovalEscalated, or null for a comment. */
    private String eventName;
    private Instant occurredAt;
    private String actorId;
    private String body;
    private String fromTier;
    private String toTier;
}
