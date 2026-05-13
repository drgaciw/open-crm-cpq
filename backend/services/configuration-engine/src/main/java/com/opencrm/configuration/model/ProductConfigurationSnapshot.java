package com.opencrm.configuration.model;

import java.util.List;

public record ProductConfigurationSnapshot(
        Long productId,
        List<ConfigurationRuleSnapshot> rules,
        List<BundleComponentSnapshot> bundleComponents
) {
}
