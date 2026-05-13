package com.opencrm.configuration.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Configuration model compiler")
class ConfigurationModelCompilerTest {

    @Test
    @DisplayName("should include rules and bundle components in compiled model")
    void compilesSnapshotIntoModel() {
        ProductConfigurationSnapshot snapshot = new ProductConfigurationSnapshot(
                10L,
                List.of(new ConfigurationRuleSnapshot(1L, "REQUIRES", 10L, 11L, 100, "selected", "Required")),
                List.of(new BundleComponentSnapshot(10L, 12L, 2, false))
        );
        ConfigurationModelCompiler compiler = new ConfigurationModelCompiler(productId -> snapshot);

        ConfigurationModel model = compiler.compile(10L);

        assertEquals(10L, model.productId());
        assertEquals(1, model.rules().size());
        assertEquals(1, model.bundleComponents().size());
        assertTrue(model.referencedProductIds().containsAll(List.of(10L, 11L, 12L)));
        assertEquals(64, model.version().length());
    }
}
