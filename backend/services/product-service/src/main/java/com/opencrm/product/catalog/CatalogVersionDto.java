package com.opencrm.product.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CatalogVersionDto {

    /** Catalog version id — pin value for Configuration StartSession and Pricing. */
    private String versionId;
    private Integer versionNumber;
    private String tenantId;
    private Instant publishedAt;
    private String publishedBy;
    private int productCount;
}
