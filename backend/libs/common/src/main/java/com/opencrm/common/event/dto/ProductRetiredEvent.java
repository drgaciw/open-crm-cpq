package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when a product is retired.
 * Topic: cpq.product.status.retired
 * Publisher: Product Catalog
 * Subscribers: Configuration Engine, Quote Management
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductRetiredEvent {
    private String productId;
    private Instant retiredAt;
    private String reasonCode;
}
