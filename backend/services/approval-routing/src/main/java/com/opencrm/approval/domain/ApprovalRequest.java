package com.opencrm.approval.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Approval request entity with state machine transitions.
 * Ensures explicit state transitions with traceability.
 */
@Entity
@Table(name = "approval_requests", indexes = {
    @Index(name = "idx_approval_quote", columnList = "quote_id"),
    @Index(name = "idx_approval_status", columnList = "status"),
    @Index(name = "idx_approval_type", columnList = "approval_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ApprovalRequest extends BaseEntity {
    
    @Column(name = "quote_id", nullable = false)
    private String quoteId;
    
    @Column(name = "line_id")
    private String lineId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_type", nullable = false, length = 50)
    private ApprovalType approvalType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;
    
    @Column(name = "requested_discount", precision = 19, scale = 2)
    private BigDecimal requestedDiscount;
    
    @Column(name = "margin_delta", precision = 19, scale = 4)
    private BigDecimal marginDelta;
    
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "pricing_fingerprint", length = 64)
    private String pricingFingerprint;

    @Column(name = "governing_period")
    private Integer governingPeriod;
    
    @Column(name = "requester_id", nullable = false)
    private String requesterId;
    
    @Column(name = "approver_id")
    private String approverId;
    
    @Column(name = "approval_tier", length = 50)
    private String approvalTier;
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;
    
    @Column(name = "submitted_at")
    private Instant submittedAt;
    
    @Column(name = "approved_at")
    private Instant approvedAt;
    
    @Column(name = "rejected_at")
    private Instant rejectedAt;

    /** The request that replaced this one, when {@link ApprovalStatus#SUPERSEDED}. */
    @Column(name = "superseded_by")
    private Long supersededBy;

    @Column(name = "superseded_at")
    private Instant supersededAt;
    
    // State machine transitions
    public void approve(String approverId) {
        if (status != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Can only approve PENDING requests");
        }
        this.status = ApprovalStatus.APPROVED;
        this.approverId = approverId;
        this.approvedAt = Instant.now();
    }
    
    public void reject(String approverId, String rejectionReason) {
        if (status != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Can only reject PENDING requests");
        }
        this.status = ApprovalStatus.REJECTED;
        this.approverId = approverId;
        this.rejectionReason = rejectionReason;
        this.rejectedAt = Instant.now();
    }
    
    public void cancel() {
        if (status == ApprovalStatus.APPROVED || status == ApprovalStatus.REJECTED
                || status == ApprovalStatus.SUPERSEDED) {
            throw new IllegalStateException("Cannot cancel already processed requests");
        }
        this.status = ApprovalStatus.CANCELLED;
    }

    /**
     * Replaces this open request by a newer one for the same quote line (owner decision 9). Only a PENDING request can
     * be superseded: a decided one is history that the new figures do not change.
     */
    public void supersede(Long newerRequestId) {
        if (status != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Can only supersede PENDING requests");
        }
        java.util.Objects.requireNonNull(newerRequestId, "the request that supersedes this one");
        this.status = ApprovalStatus.SUPERSEDED;
        this.supersededBy = newerRequestId;
        this.supersededAt = Instant.now();
    }
}
