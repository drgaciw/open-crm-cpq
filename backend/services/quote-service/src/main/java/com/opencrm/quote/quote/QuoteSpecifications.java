package com.opencrm.quote.quote;

import org.springframework.data.jpa.domain.Specification;

/**
 * Composable JPA Specifications for Quote filtering.
 * Allows combining multiple filters (status, dealType, customer) simultaneously
 * instead of mutually exclusive if/else branches.
 */
public final class QuoteSpecifications {

    private QuoteSpecifications() {
    }

    public static Specification<Quote> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<Quote> hasStatus(QuoteStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Quote> hasDealType(DealType dealType) {
        return (root, query, cb) -> cb.equal(root.get("dealType"), dealType);
    }

    public static Specification<Quote> hasCustomerExternalId(String customerExternalId) {
        return (root, query, cb) -> cb.equal(root.get("customerExternalId"), customerExternalId);
    }
}
