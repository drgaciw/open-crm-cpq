package com.opencrm.product.catalog;

import com.opencrm.common.entity.BaseEntity;
import com.opencrm.common.valueobject.Money;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_sku", columnList = "sku"),
    @Index(name = "idx_product_type", columnList = "product_type"),
    @Index(name = "idx_product_category", columnList = "category"),
    @Index(name = "idx_product_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Product extends BaseEntity {

    @Column(name = "sku", nullable = false, unique = true, length = 100)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 50)
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private ProductCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    // Money value object for list price (no primitive float/double)
    @Column(name = "list_price", precision = 19, scale = 2)
    private BigDecimal listPrice;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_frequency", length = 20)
    private BillingFrequency billingFrequency;

    @Column(name = "unit_of_measure", length = 50)
    private String unitOfMeasure;

    @Column(name = "is_configurable", nullable = false)
    @Builder.Default
    private boolean configurable = false;

    @Column(name = "is_bundle", nullable = false)
    @Builder.Default
    private boolean bundle = false;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "retirement_reason", length = 500)
    private String retirementReason;

    /**
     * Entra {@code tid} of the caller who defined this product — the same deliberately weak per-deployment
     * interim quote-service uses (DRG-632, ADR-016; ADR-036 "account-tenant ownership" is Proposed, not accepted,
     * and authorizes no verified per-customer tenant). Null for service tokens, anonymous callers, dev mode, and
     * rows from before this column. SKU uniqueness ({@code uq_products_tenant_sku}, V1_9) is scoped by this value;
     * never set from a request body, and never changed after create.
     */
    @Column(name = "tenant_id", length = 128, updatable = false)
    private String tenantId;

    /** DRG-418 / BC-01-01: EffectiveDateRange start. Null means sellable immediately. */
    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    /** DRG-418 / BC-01-01: EffectiveDateRange end. Null means no planned end. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /**
     * DRG-418 / BC-01-01: the only legal LifecycleState transitions. Draft -> Active -> Retired, plus the
     * pre-existing Active -> Deprecated -> Retired branch. The single source of truth for {@link #activate()},
     * {@link #deprecate()}, {@link #retire(String)} and {@code ProductService}'s revise path.
     */
    private static final Map<ProductStatus, Set<ProductStatus>> ALLOWED_TRANSITIONS = Map.of(
            ProductStatus.DRAFT, Set.of(ProductStatus.ACTIVE),
            ProductStatus.ACTIVE, Set.of(ProductStatus.DEPRECATED, ProductStatus.RETIRED),
            ProductStatus.DEPRECATED, Set.of(ProductStatus.RETIRED),
            ProductStatus.RETIRED, Set.of()
    );

    /** Whether {@code from -> to} is a legal LifecycleState transition. Equal states are not a transition. */
    public static boolean isLegalTransition(ProductStatus from, ProductStatus to) {
        return from != to && ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    // Helper method to get Money object
    @Transient
    public Money getListPriceMoney() {
        if (listPrice == null) {
            return Money.zero(currency);
        }
        return Money.of(listPrice, currency);
    }

    // State machine transitions
    public void activate() {
        if (!isLegalTransition(status, ProductStatus.ACTIVE)) {
            throw new IllegalStateException("Cannot transition product from " + status + " to ACTIVE");
        }
        this.status = ProductStatus.ACTIVE;
    }

    public void deprecate() {
        if (!isLegalTransition(status, ProductStatus.DEPRECATED)) {
            throw new IllegalStateException("Cannot transition product from " + status + " to DEPRECATED");
        }
        this.status = ProductStatus.DEPRECATED;
    }

    public void retire(String reason) {
        if (!isLegalTransition(status, ProductStatus.RETIRED)) {
            throw new IllegalStateException("Cannot transition product from " + status + " to RETIRED");
        }
        this.status = ProductStatus.RETIRED;
        this.retiredAt = Instant.now();
        this.retirementReason = reason;
    }
}
