package com.opencrm.configuration.model;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmptyProductConfigurationSnapshotProvider implements ProductConfigurationSnapshotProvider {

    @Override
    public ProductConfigurationSnapshot load(Long productId) {
        return new ProductConfigurationSnapshot(productId, List.of(), List.of());
    }
}
