package com.opencrm.product.catalog;

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
 * Immutable published catalog version header (DRG-421 / BC-01-04).
 * {@link #getUuid()} is the version id carried by CatalogPublished and used as the pin value.
 */
@Entity
@Table(name = "catalog_versions", indexes = {
        @Index(name = "idx_catalog_versions_tenant_published", columnList = "tenant_id, published_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CatalogVersion extends BaseEntity {

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "tenant_id", length = 128, updatable = false)
    private String tenantId;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    @Column(name = "published_by", length = 100, updatable = false)
    private String publishedBy;

    @Column(name = "product_count", nullable = false)
    private int productCount;
}
