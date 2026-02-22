package com.opencrm.pricing.product;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * PricingProduct entity - lightweight product reference for pricing.
 */
@Entity
@Table(name = "pricing_products", indexes = {
    @Index(name = "idx_pricing_product_external_id", columnList = "external_id"),
    @Index(name = "idx_pricing_product_type", columnList = "product_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PricingProduct extends BaseEntity {

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private ProductCategory category;

    @Column(name = "list_price", precision = 19, scale = 4)
    private BigDecimal listPrice;

    @Column(name = "currency")
    @Builder.Default
    private String currency = "USD";

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
