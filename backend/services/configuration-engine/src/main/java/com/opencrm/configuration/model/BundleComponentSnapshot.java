package com.opencrm.configuration.model;

public record BundleComponentSnapshot(
        Long bundleProductId,
        Long componentProductId,
        int quantity,
        boolean optional
) {
}
