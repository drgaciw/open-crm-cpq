package com.opencrm.configuration.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Line item within a product bundle, with optional pricing overrides.
 */
@Entity
@Table(name = "config_bundle_line_items", indexes = {
    @Index(name = "idx_cfg_bli_bundle_id", columnList = "bundle_id"),
    @Index(name = "idx_cfg_bli_product_id", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BundleLineItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bundle_id", nullable = false)
    private ProductBundle bundle;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private int quantity = 1;

    @Column(name = "min_quantity")
    @Builder.Default
    private int minQuantity = 1;

    @Column(name = "max_quantity")
    private Integer maxQuantity;

    @Column(name = "is_optional", nullable = false)
    @Builder.Default
    private boolean optional = false;

    @Column(name = "price_override", precision = 19, scale = 2)
    private BigDecimal priceOverride;

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;
}
