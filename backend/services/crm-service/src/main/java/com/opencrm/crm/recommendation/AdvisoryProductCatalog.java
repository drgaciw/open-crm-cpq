package com.opencrm.crm.recommendation;

import com.opencrm.crm.deal.DealType;

import java.util.List;
import java.util.Set;

/**
 * Local retrieval corpus for advisory cross-sell. These keys are not read from product-service.
 * A {@link CatalogEligibilityClient}, when one is configured, drops any key the sellable catalog rejects.
 */
final class AdvisoryProductCatalog {

    private AdvisoryProductCatalog() {
    }

    record Product(
            String productId,
            String name,
            Set<DealType> dealTypes,
            Set<String> industries,
            List<String> keywords) {
    }

    /**
     * Returned, at the base confidence, only when the deal matches no catalog, account, or win signal.
     * Kept in catalog order; the ranker sorts ties by product id.
     */
    static final List<String> GENERAL_COMPANION_IDS = List.of(
            "services-onboarding",
            "subscription-platform",
            "support-success-plan");

    static final List<Product> PRODUCTS = List.of(
            new Product(
                    "support-success-plan",
                    "Success plan",
                    Set.of(DealType.RENEWAL, DealType.EXISTING_BUSINESS),
                    Set.of(),
                    List.of("support", "success", "renew", "retention")),
            new Product(
                    "addon-analytics",
                    "Analytics add-on",
                    Set.of(DealType.UPSELL, DealType.CROSS_SELL, DealType.NEW_BUSINESS),
                    Set.of("software", "technology", "saas", "information"),
                    List.of("analytics", "insight", "dashboard", "data")),
            new Product(
                    "services-onboarding",
                    "Onboarding services",
                    Set.of(DealType.NEW_BUSINESS, DealType.CROSS_SELL),
                    Set.of(),
                    List.of("onboard", "implementation", "launch", "services")),
            new Product(
                    "license-expansion",
                    "License expansion",
                    Set.of(DealType.UPSELL, DealType.EXISTING_BUSINESS, DealType.RENEWAL),
                    Set.of(),
                    List.of("license", "seat", "expansion", "user")),
            new Product(
                    "subscription-platform",
                    "Platform subscription",
                    Set.of(DealType.NEW_BUSINESS, DealType.CROSS_SELL),
                    Set.of(),
                    List.of("platform", "subscription", "suite")));

    static Product require(String productId) {
        for (Product product : PRODUCTS) {
            if (product.productId().equals(productId)) {
                return product;
            }
        }
        throw new IllegalArgumentException("Unknown advisory catalog key: " + productId);
    }
}
