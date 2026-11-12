package com.opencrm.product.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Minimal pin contract for Configuration and Pricing (DRG-421).
 * Consumers store {@link #versionId} and may re-resolve via GET /api/v1/catalog/versions/{versionId}/pin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CatalogPinDto {

    private String versionId;
    private Integer versionNumber;
    private Instant publishedAt;
    private int productCount;
    private boolean resolvable;
}
