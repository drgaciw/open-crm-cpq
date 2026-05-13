package com.opencrm.configuration.model;

public interface ProductConfigurationSnapshotProvider {

    ProductConfigurationSnapshot load(Long productId);
}
