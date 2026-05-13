package com.opencrm.quote.quote;

import com.opencrm.common.entity.BaseEntity;
import com.opencrm.common.valueobject.Money;
import com.opencrm.quote.lineitem.QuoteLineItem;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "quotes", indexes = {
    @Index(name = "idx_quote_number", columnList = "quote_number"),
    @Index(name = "idx_quote_status", columnList = "status"),
    @Index(name = "idx_quote_deal_type", columnList = "deal_type"),
    @Index(name = "idx_quote_customer", columnList = "customer_external_id"),
    @Index(name = "idx_quote_approval", columnList = "approval_state")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Quote extends BaseEntity {

    @Column(name = "quote_number", nullable = false, unique = true, length = 50)
    private String quoteNumber;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "customer_external_id", nullable = false)
    private String customerExternalId;

    @Column(name = "deal_external_id")
    private String dealExternalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private QuoteStatus status = QuoteStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "deal_type", nullable = false, length = 30)
    @Builder.Default
    private DealType dealType = DealType.NEW;

    @Column(name = "subscription_type", length = 30)
    private String subscriptionType;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "region", length = 20)
    private String region;

    @Column(name = "duration_in_months")
    @Builder.Default
    private int durationInMonths = 12;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "subtotal", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "total_discount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalDiscount = BigDecimal.ZERO;

    @Column(name = "total_tax", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalTax = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "arr", precision = 19, scale = 2)
    private BigDecimal arr;

    @Column(name = "tcv", precision = 19, scale = 2)
    private BigDecimal tcv;

    @Column(name = "acv", precision = 19, scale = 2)
    private BigDecimal acv;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_state", length = 30)
    @Builder.Default
    private ApprovalState approvalState = ApprovalState.NONE;

    @Column(name = "approval_tier", length = 50)
    private String approvalTier;

    @Column(name = "pricing_waterfall_json", columnDefinition = "TEXT")
    private String pricingWaterfallJson;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @SQLRestriction("is_deleted = false")
    @Builder.Default
    private List<QuoteLineItem> lineItems = new ArrayList<>();
    
    @Column(name = "accepted_at")
    private Instant acceptedAt;
    
    public void addLineItem(QuoteLineItem item) {
        ensureMutable();
        Objects.requireNonNull(item, "line item is required");

        Quote owner = item.getQuote();
        if (owner != null && !sameQuote(owner)) {
            throw new IllegalArgumentException("Line item already belongs to another quote");
        }

        item.setQuote(this);
        if (!lineItems.contains(item)) {
            lineItems.add(item);
        }
        recalculateTotals();
    }

    public void removeLineItem(QuoteLineItem item) {
        ensureMutable();
        Objects.requireNonNull(item, "line item is required");

        if (!sameQuote(item.getQuote())) {
            throw new IllegalArgumentException("Line item does not belong to quote " + quoteNumber);
        }

        item.softDelete();
        lineItems.remove(item);
        recalculateTotals();
    }

    public void recalculateTotals() {
        ensureMutable();

        BigDecimal subtotalSum = BigDecimal.ZERO;
        BigDecimal discountSum = BigDecimal.ZERO;
        BigDecimal totalSum = BigDecimal.ZERO;
        
        for (QuoteLineItem item : lineItems) {
            if (!item.isDeleted()) {
                BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());
                BigDecimal listPrice = valueOrZero(item.getListPrice());
                BigDecimal discountAmount = valueOrZero(item.getDiscountAmount());
                BigDecimal extendedAmount = item.getExtendedAmount() != null
                        ? item.getExtendedAmount()
                        : listPrice.multiply(quantity).subtract(discountAmount);

                subtotalSum = subtotalSum.add(listPrice.multiply(quantity));
                discountSum = discountSum.add(discountAmount);
                totalSum = totalSum.add(extendedAmount);
            }
        }
        
        this.subtotal = subtotalSum;
        this.totalDiscount = discountSum;
        this.totalAmount = totalSum;
    }
    
    /**
     * State machine transition to ACCEPTED status.
     */
    public void accept() {
        accept(Instant.now());
    }

    public void accept(Instant acceptedAt) {
        if (status != QuoteStatus.SENT) {
            throw new IllegalStateException("Can only accept quotes in SENT status");
        }
        recalculateTotals();
        this.status = QuoteStatus.ACCEPTED;
        this.acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt is required");
    }
    
    /**
     * Helper method to get Money objects for totals.
     */
    @Transient
    public Money getTotalAmountMoney() {
        return Money.of(totalAmount, currency);
    }

    private void ensureMutable() {
        if (status == QuoteStatus.ACCEPTED) {
            throw new IllegalStateException("Cannot mutate an accepted quote");
        }
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private boolean sameQuote(Quote other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        return getId() != null && getId().equals(other.getId());
    }
}
