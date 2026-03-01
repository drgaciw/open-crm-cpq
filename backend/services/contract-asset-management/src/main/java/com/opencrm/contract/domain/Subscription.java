package com.opencrm.contract.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    
    @Column(name = "customer_id", nullable = false)
    private String customerId;
    
    @Column(name = "quote_id", nullable = false)
    private String quoteId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.PENDING;
    
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
        this.status = SubscriptionStatus.CANCELLED;
    }
    
    public void renew(LocalDate newEndDate) {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("Can only renew ACTIVE subscriptions");
        }
        this.endDate = newEndDate;
        this.renewalDate = newEndDate.minusDays(30); // 30 days before end
    }
}
