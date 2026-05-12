package com.opencrm.common.event.dto.v1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Versioned published-language contract for {@code cpq.product.status.retired}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRetiredEventV1 {

    public static final int SCHEMA_VERSION = 1;

    @Builder.Default
    private int schemaVersion = SCHEMA_VERSION;

    private String productId;
    private Instant retiredAt;
    private String reasonCode;
}
