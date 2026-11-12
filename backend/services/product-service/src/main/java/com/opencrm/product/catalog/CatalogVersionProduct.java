package com.opencrm.product.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Frozen product row belonging to a {@link CatalogVersion}. Seller-facing reads use these snapshots.
 */
@Entity
@Table(name = "catalog_version_products", indexes = {
        @Index(name = "idx_catalog_version_products_version", columnList = "catalog_version_id"),
        @Index(name = "idx_catalog_version_products_sku", columnList = "catalog_version_id, sku")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogVersionProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "catalog_version_id", nullable = false, updatable = false)
    private Long catalogVersionId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "product_uuid", nullable = false, length = 36, updatable = false)
    private String productUuid;

    @Column(name = "sku", nullable = false, length = 100, updatable = false)
    private String sku;

    @Column(name = "name", nullable = false, updatable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT", updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 50, updatable = false)
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50, updatable = false)
    private ProductCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, updatable = false)
    private ProductStatus status;

    @Column(name = "list_price", precision = 19, scale = 2, updatable = false)
    private BigDecimal listPrice;

    @Column(name = "currency", length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_frequency", length = 20, updatable = false)
    private BillingFrequency billingFrequency;

    @Column(name = "unit_of_measure", length = 50, updatable = false)
    private String unitOfMeasure;

    @Column(name = "effective_from", updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to", updatable = false)
    private LocalDate effectiveTo;

    @Column(name = "is_configurable", nullable = false, updatable = false)
    private boolean configurable;

    @Column(name = "is_bundle", nullable = false, updatable = false)
    private boolean bundle;
}
