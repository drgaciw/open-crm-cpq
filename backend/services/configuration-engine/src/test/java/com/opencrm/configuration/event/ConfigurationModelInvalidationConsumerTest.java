package com.opencrm.configuration.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.configuration.model.ConfigurationModelCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Configuration model invalidation consumer")
class ConfigurationModelInvalidationConsumerTest {

    @Test
    @DisplayName("should invalidate product model from product events")
    void invalidatesProductModels() {
        ConfigurationModelCache cache = mock(ConfigurationModelCache.class);
        ConfigurationModelInvalidationConsumer consumer =
                new ConfigurationModelInvalidationConsumer(cache, new ObjectMapper());

        consumer.handleProductChanged("{\"productId\":\"42\"}");

        verify(cache).invalidateProduct(42L);
    }

    @Test
    @DisplayName("should invalidate bundle model from bundle events")
    void invalidatesBundleModels() {
        ConfigurationModelCache cache = mock(ConfigurationModelCache.class);
        ConfigurationModelInvalidationConsumer consumer =
                new ConfigurationModelInvalidationConsumer(cache, new ObjectMapper());

        consumer.handleBundleChanged("{\"productId\":99}");

        verify(cache).invalidateBundle(99L);
    }
}
