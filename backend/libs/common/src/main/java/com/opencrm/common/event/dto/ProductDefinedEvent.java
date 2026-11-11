package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Event published when a new Product definition is created (DRG-418 / BC-01-01: DefineProduct).
 * Topic: cpq.product.defined
 * Publisher: Product Catalog
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDefinedEvent {
    private String productId;
    private String sku;
    private String name;
    private String unitOfMeasure;
    private String lifecycleState;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Instant definedAt;
}
