package com.opencrm.contract.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Subscription entity - authoritative source for subscription/asset state.
 */
@Entity
@Table(name = "subscriptions", indexes = {
    @Index(name = "idx_subscription_customer", columnList = "customer_id"),
    @Index(name = "idx_subscription_status", columnList = "status"),
    @Index(name = "idx_subscription_renewal", columnList = "renewal_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Subscription extends BaseEntity {
    
    @Column(name = "subscription_number", nullable = false, unique = true)
    private String subscriptionNumber;

    /**
     * tenantId is the Entra `tid` claim: a per-deployment value (single issuer per deployment, ADR-016 / DRG-632), deliberately weak interim. It is NOT a real per-tenant id.
     *
     * <p>Set once at creation from QuoteAccepted top-level {@code tenantId} (DRG-631).
     * Null when absent/non-GUID. Must equal {@code contracts.tenant_id} when
     * {@code contract_id} is set. No public setter after creation ({@code updatable = false}).
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;
    
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    /**
     * Typed CRM account uuid (DRG-663). Stays NULL at QuoteAccepted ingest —
     * {@code customerId} is a String external id and must not be parsed into this column.
     * When {@code contract_id} is set, must equal {@code contracts.customer_account_id}.
     */
    @Column(name = "customer_account_id")
    private UUID customerAccountId;
    
    @Column(name = "quote_id", nullable = false)
    private String quoteId;

    /**
     * Optional link to a canonical {@code contract_lines} row (DRG-665).
     * Nullable while QuoteAccepted remains the interim origin; not populated by that path yet.
     */
    @Column(name = "contract_line_id")
    private UUID contractLineId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.PENDING;

    /** ADR §2.2 charge model (DRG-666). Null on legacy rows until an owner-gated S7 backfill. */
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_model", length = 32)
    private SubscriptionModel subscriptionModel;
    
    @Column(name = "plan_name", nullable = false)
    private String planName;
    
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    
    @Column(name = "renewal_date")
    private LocalDate renewalDate;
    
    @Column(name = "mrr", precision = 19, scale = 2)
    private BigDecimal mrr;
    
    @Column(name = "arr", precision = 19, scale = 2)
    private BigDecimal arr;
    
    @Column(name = "currency_code", length = 3)
    @Builder.Default
    private String currencyCode = "USD";
    
    @Column(name = "usage_band", length = 50)
    private String usageBand;
    
    @Column(name = "erp_sync_status", length = 20)
    @Builder.Default
    private String erpSyncStatus = "PENDING";
    
    @Column(name = "erp_subscription_id")
    private String erpSubscriptionId;

    @Column(name = "commercial_revision", nullable = false)
    @Builder.Default
    private Long commercialRevision = 0L;

    @Column(name = "erp_sync_claim_token", length = 36)
    private String erpSyncClaimToken;

    @Column(name = "erp_sync_claim_revision")
    private Long erpSyncClaimRevision;
    
    public void activate() {
        if (status != SubscriptionStatus.PENDING) {
            throw new IllegalStateException("Can only activate PENDING subscriptions");
        }
        this.status = SubscriptionStatus.ACTIVE;
    }
    
    public void suspend() {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("Can only suspend ACTIVE subscriptions");
        }
        this.status = SubscriptionStatus.SUSPENDED;
    }
    
    public void cancel() {
        if (status != SubscriptionStatus.PENDING && status != SubscriptionStatus.ACTIVE
                && status != SubscriptionStatus.SUSPENDED) {
            throw new IllegalStateException("Can only cancel PENDING, ACTIVE or SUSPENDED subscriptions");
        }
        this.status = SubscriptionStatus.CANCELLED;
    }
}
