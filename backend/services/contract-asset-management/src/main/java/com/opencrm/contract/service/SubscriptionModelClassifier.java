package com.opencrm.contract.service;

import com.opencrm.contract.domain.SubscriptionModel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RED stub for DRG-666. Implementation fills the ADR §2.2 mapping table.
 */
public final class SubscriptionModelClassifier {
    private SubscriptionModelClassifier() {}

    public static Map<String, SubscriptionModel> mappingTable() {
        return Map.of();
    }

    public static Optional<SubscriptionModel> map(String productType) {
        return Optional.empty();
    }

    public static Optional<SubscriptionModel> classifyLines(List<String> productTypes) {
        return Optional.empty();
    }
}
