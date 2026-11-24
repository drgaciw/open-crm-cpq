package com.opencrm.approval.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * A comment on an approval case. {@link #parentCommentId} is the thread parent.
 * The case is the approval request. There is no separate deal aggregate.
 */
@Entity
@Table(name = "approval_case_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ApprovalCaseComment extends BaseEntity {

    @Column(name = "approval_request_id", nullable = false)
    private Long approvalRequestId;

    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Column(name = "quote_id", nullable = false)
    private String quoteId;

    /** Quote {@code version} at comment time, stamped by the quote BFF. */
    @Column(name = "quote_version")
    private Long quoteVersion;

    @Column(name = "author_id", nullable = false)
    private String authorId;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "commented_at", nullable = false)
    private Instant commentedAt;
}
