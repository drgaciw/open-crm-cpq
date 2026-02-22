package com.opencrm.quote.approval;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "approval_requests", indexes = {
    @Index(name = "idx_approval_quote_id", columnList = "quote_id"),
    @Index(name = "idx_approval_status", columnList = "status"),
    @Index(name = "idx_approval_tier", columnList = "tier")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ApprovalRequest extends BaseEntity {

    @Column(name = "quote_id", nullable = false)
    private Long quoteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 50)
    private ApprovalTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "approver", length = 100)
    private String approver;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private Instant requestedAt = Instant.now();

    @Column(name = "responded_at")
    private Instant respondedAt;
}
