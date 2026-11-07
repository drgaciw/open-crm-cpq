package com.opencrm.contract.service;

import com.opencrm.contract.domain.SubscriptionModel;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ADR §2.2 product-type → {@link SubscriptionModel} mapping (DRG-666).
 *
 * <p>Seeded from today's {@code AcceptedQuoteSubscriptionService} allow-list
 * ({@code SAAS_FLAT_RATE}, {@code ONPREM_SUBSCRIPTION}, {@code SUPPORT_*}) plus the typed
 * fixed-recurring labels already accepted by {@link AcceptedRampValidator}.
 */
public final class SubscriptionModelClassifier {
    private static final Map<String, SubscriptionModel> MAPPING;

    static {
        Map<String, SubscriptionModel> table = new LinkedHashMap<>();
        // Allow-list seed (AcceptedQuoteSubscriptionService L98-99 at bf41c230).
        table.put("SAAS_FLAT_RATE", SubscriptionModel.SAAS_RECURRING);
        table.put("ONPREM_SUBSCRIPTION", SubscriptionModel.ONPREM_TRANSACTIONAL);
        table.put("SUPPORT_BASIC", SubscriptionModel.SAAS_RECURRING);
        table.put("SUPPORT_PREMIUM", SubscriptionModel.SAAS_RECURRING);
        table.put("SUPPORT_ENTERPRISE", SubscriptionModel.SAAS_RECURRING);
        // Typed fixed-recurring labels already accepted at ingest.
        table.put("SAAS_SEAT_NAMED", SubscriptionModel.SAAS_RECURRING);
        table.put("SAAS_SEAT_CONCURRENT", SubscriptionModel.SAAS_RECURRING);
        table.put("ONPREM_SUPPORT", SubscriptionModel.ONPREM_TRANSACTIONAL);
        table.put("ONPREM_CAPACITY", SubscriptionModel.ONPREM_CAPACITY);
        table.put("HYBRID_MIGRATION_RAMP", SubscriptionModel.SAAS_RECURRING);
        MAPPING = Map.copyOf(table);
    }

    private SubscriptionModelClassifier() {}

    public static Map<String, SubscriptionModel> mappingTable() {
        return MAPPING;
    }

    public static Optional<SubscriptionModel> map(String productType) {
        if (productType == null || productType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(MAPPING.get(productType.trim().toUpperCase(Locale.ROOT)));
    }

    /**
     * Resolves one model for a NEW sale. Unanimous models win. A hybrid ramp paired with
     * on-prem support resolves to {@link SubscriptionModel#SAAS_RECURRING} (destination).
     * Other mixed sets are empty (caller treats as rejectable).
     */
    public static Optional<SubscriptionModel> classifyLines(List<String> productTypes) {
        if (productTypes == null || productTypes.isEmpty()) {
            return Optional.empty();
        }
        Set<SubscriptionModel> models = new LinkedHashSet<>();
        boolean hybridRamp = false;
        for (String type : productTypes) {
            if (type == null || type.isBlank()) {
                continue;
            }
            String normalized = type.trim().toUpperCase(Locale.ROOT);
            if ("HYBRID_MIGRATION_RAMP".equals(normalized)) {
                hybridRamp = true;
            }
            Optional<SubscriptionModel> mapped = map(normalized);
            if (mapped.isEmpty()) {
                return Optional.empty();
            }
            models.add(mapped.get());
        }
        if (models.isEmpty()) {
            return Optional.empty();
        }
        if (models.size() == 1) {
            return Optional.of(models.iterator().next());
        }
        if (hybridRamp && models.contains(SubscriptionModel.SAAS_RECURRING)) {
            return Optional.of(SubscriptionModel.SAAS_RECURRING);
        }
        return Optional.empty();
    }
}
