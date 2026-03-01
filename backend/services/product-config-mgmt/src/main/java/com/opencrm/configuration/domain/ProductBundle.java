package com.opencrm.configuration.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate root for product bundles with pricing overrides and quantity restrictions.
 */
@Entity
@Table(name = "config_product_bundles", indexes = {
    @Index(name = "idx_cfg_bundle_code", columnList = "bundle_code"),
    @Index(name = "idx_cfg_bundle_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductBundle extends BaseEntity {

    @Column(name = "bundle_code", nullable = false, unique = true, length = 100)
    private String bundleCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ConfigurationStatus status = ConfigurationStatus.DRAFT;

    @Column(name = "discount_type", length = 20)
    @Builder.Default
    private String discountType = "NONE";

    @Column(name = "discount_value", precision = 19, scale = 4)
    private BigDecimal discountValue;

    @Column(name = "fixed_price", precision = 19, scale = 2)
    private BigDecimal fixedPrice;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "min_quantity")
    @Builder.Default
    private int minQuantity = 1;

    @Column(name = "max_quantity")
    private Integer maxQuantity;

    @Column(name = "valid_from")
    private java.time.Instant validFrom;

    @Column(name = "valid_to")
    private java.time.Instant validTo;

    @OneToMany(mappedBy = "bundle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BundleLineItem> lineItems = new ArrayList<>();

    public void addLineItem(BundleLineItem item) {
        lineItems.add(item);
        item.setBundle(this);
    }

    public void removeLineItem(BundleLineItem item) {
        lineItems.remove(item);
        item.setBundle(null);
    }

    public void activate() {
        if (this.status == ConfigurationStatus.ARCHIVED) {
            throw new IllegalStateException("Cannot activate an archived bundle");
        }
        this.status = ConfigurationStatus.ACTIVE;
    }

    public void deprecate() {
        if (this.status != ConfigurationStatus.ACTIVE) {
            throw new IllegalStateException("Only active bundles can be deprecated");
        }
        this.status = ConfigurationStatus.DEPRECATED;
    }

    public void archive() {
        if (this.status == ConfigurationStatus.ACTIVE) {
            throw new IllegalStateException("Cannot archive an active bundle; deprecate it first");
        }
        this.status = ConfigurationStatus.ARCHIVED;
    }
}
