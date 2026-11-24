package com.opencrm.approval.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * One GetApprovalHistory row: a decision, an escalation, or a comment, in time order.
 */
@Entity
@Table(name = "approval_timeline_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ApprovalTimelineEntry extends BaseEntity {

    @Column(name = "approval_request_id", nullable = false)
    private Long approvalRequestId;

    @Column(name = "quote_id", nullable = false)
    private String quoteId;

    @Column(name = "quote_version")
    private Long quoteVersion;

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_kind", nullable = false, length = 32)
    private ApprovalTimelineKind kind;

    @Column(name = "event_name", length = 64)
    private String eventName;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "from_tier", length = 50)
    private String fromTier;

    @Column(name = "to_tier", length = 50)
    private String toTier;
}
