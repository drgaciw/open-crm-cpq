package com.opencrm.configuration.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Compiled configuration schema snapshot for a product.
 */
public record ConfigurationModel(
        String productId,
        String schemaVersion,
        Instant compiledAt
) {
    public ConfigurationModel {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(compiledAt, "compiledAt");
    }
}
