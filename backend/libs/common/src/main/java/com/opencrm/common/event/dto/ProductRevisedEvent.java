package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Event published when an existing Product definition is revised (DRG-418 / BC-01-01: ReviseProduct).
 * Topic: cpq.product.revised
 * Publisher: Product Catalog
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductRevisedEvent {
    private String productId;
    private String sku;
    private String unitOfMeasure;
    private String lifecycleState;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Instant revisedAt;
}
