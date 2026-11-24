package com.opencrm.contract.documents;

import java.util.UUID;

/**
 * Server-side quote and catalog reads for DRG-594. Callers must not pass a client flag through this port.
 */
public interface LegalReviewEvidence {

    LegalReviewClassifier.QuoteMembership quoteMembership(UUID quoteId);

    LegalReviewClassifier.CatalogProduct catalogProduct(String sku);
}
