package com.opencrm.contract.documents;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reads the persisted quote lines and the product catalog. It does not accept a client classification.
 */
@Component
class PersistedQuoteCatalogEvidence implements LegalReviewEvidence {

    private final EntityManager entityManager;

    PersistedQuoteCatalogEvidence(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public LegalReviewClassifier.QuoteMembership quoteMembership(UUID quoteId) {
        if (quoteId == null) {
            return new LegalReviewClassifier.QuoteMembership(false, Set.of());
        }
        List<?> quoteRows = entityManager.createNativeQuery(
                        "select q.id from quote.quotes q where q.uuid = :uuid and q.is_deleted = false")
                .setParameter("uuid", quoteId.toString())
                .getResultList();
        if (quoteRows.size() != 1 || !(quoteRows.get(0) instanceof Number quoteRowId)) {
            return new LegalReviewClassifier.QuoteMembership(false, Set.of());
        }
        List<?> skuRows = entityManager.createNativeQuery(
                        "select li.product_external_id from quote.quote_line_items li "
                                + "where li.quote_id = :quoteId and li.is_deleted = false")
                .setParameter("quoteId", quoteRowId.longValue())
                .getResultList();
        Set<String> skus = new LinkedHashSet<>();
        for (Object sku : skuRows) {
            if (sku != null && !sku.toString().isBlank()) {
                skus.add(sku.toString().trim());
            }
        }
        return new LegalReviewClassifier.QuoteMembership(true, Set.copyOf(skus));
    }

    @Override
    public LegalReviewClassifier.CatalogProduct catalogProduct(String sku) {
        if (sku == null || sku.isBlank()) {
            return new LegalReviewClassifier.CatalogProduct(false, null);
        }
        List<?> types = entityManager.createNativeQuery(
                        "select p.product_type from product.products p where p.sku = :sku and p.is_deleted = false")
                .setParameter("sku", sku.trim())
                .getResultList();
        if (types.size() != 1 || types.get(0) == null || types.get(0).toString().isBlank()) {
            return new LegalReviewClassifier.CatalogProduct(false, null);
        }
        return new LegalReviewClassifier.CatalogProduct(true, types.get(0).toString().trim());
    }
}
