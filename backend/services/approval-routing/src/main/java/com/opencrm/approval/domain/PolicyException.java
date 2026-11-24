package com.opencrm.approval.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * A time-boxed, justified exception for one quote version and one commercial policy (BC-04-05, DRG-445).
 * This is not an approval request. {@code tenantId} is the caller tenant copied at record time so evaluation
 * cannot honor another tenant's exception. H4 owns {@code tenant_id} on approval rows; this table does not
 * alter {@code approval_requests}.
 */
@Entity
@Table(name = "policy_exceptions", indexes = {
        @Index(name = "idx_policy_exception_quote_version", columnList = "quote_id, quote_version"),
        @Index(name = "idx_policy_exception_policy", columnList = "policy_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PolicyException extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 128, updatable = false)
    private String tenantId;

    @Column(name = "quote_id", nullable = false, length = 64, updatable = false)
    private String quoteId;

    @Column(name = "quote_version", nullable = false, updatable = false)
    private Long quoteVersion;

    @Column(name = "policy_id", nullable = false, updatable = false)
    private Long policyId;

    @Column(name = "justification", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String justification;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "recorded_by", nullable = false, length = 255, updatable = false)
    private String recordedBy;

    public PolicyExceptionGrant toGrant() {
        return new PolicyExceptionGrant(quoteId, quoteVersion, policyId, tenantId, expiresAt);
    }
}
