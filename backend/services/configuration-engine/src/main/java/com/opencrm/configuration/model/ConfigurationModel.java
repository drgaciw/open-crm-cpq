package com.opencrm.configuration.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ConfigurationModel(
        Long productId,
        String version,
        List<ConfigurationRuleSnapshot> rules,
        List<BundleComponentSnapshot> bundleComponents,
        Set<Long> referencedProductIds,
        Instant compiledAt
) {
}
