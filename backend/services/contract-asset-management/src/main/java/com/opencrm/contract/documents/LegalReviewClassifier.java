package com.opencrm.contract.documents;

import com.opencrm.contract.domain.documents.ActionCode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DRAFT to IN_REVIEW legal-review classification (DRG-594 option B).
 * Facts come from {@link LegalReviewEvidence}. A client cannot set or override the result.
 */
public final class LegalReviewClassifier {

    public static final String LEGAL_REVIEW_REQUIRED = "LEGAL_REVIEW_REQUIRED";

    private static final Set<String> FREE_HEADERS = Set.of("price", "term");

    /** Catalog product_type values from product.products. Anything else is unknown and fails closed. */
    private static final Set<String> KNOWN_CATALOG_TYPES = Set.of(
            "SAAS_SEAT_NAMED", "SAAS_SEAT_CONCURRENT", "SAAS_USAGE_METERED", "SAAS_FLAT_RATE",
            "ONPREM_PERPETUAL", "ONPREM_CORE", "ONPREM_SUBSCRIPTION", "ONPREM_SUPPORT",
            "HYBRID_PORTABLE", "HYBRID_MIGRATION_RAMP",
            "SUPPORT_BASIC", "SUPPORT_PREMIUM", "SUPPORT_ENTERPRISE",
            "PROFESSIONAL_SERVICES", "TRAINING",
            "ADD_ON_API", "ADD_ON_STORAGE", "ADD_ON_ANALYTICS", "ADD_ON_SECURITY", "ADD_ON_INTEGRATION",
            "HARDWARE", "CUSTOM");

    private LegalReviewClassifier() {
    }

    public record Line(ActionCode action, String sku) {
    }

    public record Decision(boolean legalReviewRequired, String errorCode) {
        private static Decision skip() {
            return new Decision(false, null);
        }

        private static Decision required() {
            return new Decision(true, LEGAL_REVIEW_REQUIRED);
        }
    }

    public record QuoteMembership(boolean quoteFound, Set<String> skus) {
    }

    public record CatalogProduct(boolean found, String productType) {
    }

    public static Decision classify(LegalReviewEvidence evidence, UUID quoteId, List<Line> lines, Set<String> headerEdits) {
        try {
            if (requiresHeaderReview(headerEdits) || lines == null || lines.isEmpty() || evidence == null) {
                return Decision.required();
            }
            QuoteMembership membership = evidence.quoteMembership(quoteId);
            if (membership == null || !membership.quoteFound() || membership.skus() == null) {
                return Decision.required();
            }
            for (Line line : lines) {
                if (lineRequiresReview(evidence, membership, line)) {
                    return Decision.required();
                }
            }
            return Decision.skip();
        } catch (RuntimeException ex) {
            return Decision.required();
        }
    }

    /**
     * 594-F1 option B. drg owner ruling, 7:15 PM CT Nov 23 2026.
     * A new SaaS SKU, one that is not already on the quote, still requires legal review.
     * 594-F1-C stays NEEDS_OWNER with drg. To also skip new SaaS SKUs, change this method to return false.
     */
    static boolean newSaasSkuRequiresLegalReview(boolean saas, boolean alreadyOnQuote) {
        return saas && !alreadyOnQuote;
    }

    private static boolean requiresHeaderReview(Set<String> headerEdits) {
        if (headerEdits == null) {
            return true;
        }
        for (String header : headerEdits) {
            if (header == null || !FREE_HEADERS.contains(header)) {
                return true;
            }
        }
        return false;
    }

    private static boolean lineRequiresReview(LegalReviewEvidence evidence, QuoteMembership membership, Line line) {
        if (line == null || line.action() == null || line.sku() == null || line.sku().isBlank()) {
            return true;
        }
        String sku = line.sku().trim();
        if (line.action() == ActionCode.UPDATE) {
            return false;
        }
        if (line.action() != ActionCode.ADD && line.action() != ActionCode.REMOVE) {
            return true;
        }
        CatalogProduct product = evidence.catalogProduct(sku);
        if (product == null || !product.found() || product.productType() == null || product.productType().isBlank()) {
            return true;
        }
        Boolean saas = saasOrUnknown(product.productType());
        if (saas == null) {
            return true;
        }
        boolean alreadyOnQuote = membership.skus().contains(sku);
        if (saas && alreadyOnQuote) {
            return false;
        }
        return newSaasSkuRequiresLegalReview(saas, alreadyOnQuote) || !saas;
    }

    private static Boolean saasOrUnknown(String productType) {
        String normalized = productType.trim();
        if (!KNOWN_CATALOG_TYPES.contains(normalized)) {
            return null;
        }
        return normalized.startsWith("SAAS_");
    }
}
