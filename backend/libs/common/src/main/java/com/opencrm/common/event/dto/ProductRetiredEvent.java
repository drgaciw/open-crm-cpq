package com.opencrm.common.event.dto;

import com.opencrm.common.event.dto.v1.ProductRetiredEventV1;
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
public class ProductRetiredEvent {
    private String productId;
    private Instant retiredAt;
    private String reasonCode;

    public ProductRetiredEventV1 toPublishedLanguageV1() {
        return ProductRetiredEventV1.builder()
                .productId(productId)
                .retiredAt(retiredAt)
                .reasonCode(reasonCode)
                .build();
    }
}
