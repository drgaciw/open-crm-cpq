package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when PublishCatalog creates an immutable catalog version (DRG-421 / BC-01-04).
 * Topic: cpq.catalog.published
 * Publisher: product-service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogPublishedEvent {
    /** Catalog version id (catalog_versions.uuid). Configuration and Pricing pin to this value. */
    private String versionId;
    private Integer versionNumber;
    private String tenantId;
    private int productCount;
    private Instant publishedAt;
}
