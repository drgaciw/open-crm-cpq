package com.opencrm.product.catalog;

import com.opencrm.common.entity.BaseEntity;
import com.opencrm.common.valueobject.Money;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

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
        if (status != ProductStatus.DRAFT) {
            throw new IllegalStateException("Can only activate products in DRAFT status");
        }
        this.status = ProductStatus.ACTIVE;
    }
    
    public void deprecate() {
        if (status != ProductStatus.ACTIVE) {
            throw new IllegalStateException("Can only deprecate ACTIVE products");
        }
        this.status = ProductStatus.DEPRECATED;
    }
    
    public void retire(String reason) {
        if (status != ProductStatus.ACTIVE && status != ProductStatus.DEPRECATED) {
            throw new IllegalStateException("Can only retire ACTIVE or DEPRECATED products");
        }
        this.status = ProductStatus.RETIRED;
        this.retiredAt = Instant.now();
        this.retirementReason = reason;
    }
}
