package com.opencrm.contract.asset;

import com.opencrm.contract.domain.Subscription;
import com.opencrm.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tenant for an installed-base read, taken only from the authenticated caller's {@code tid}.
 * A missing or non-GUID tid is closed: every read returns nothing. Client parameters never set it.
 */
final class InstalledBaseReadScope {
    private final UUID tenantId;

    private InstalledBaseReadScope(UUID tenantId) {
        this.tenantId = tenantId;
    }

    static InstalledBaseReadScope current() {
        InstalledBaseReadAccess.require();
        return new InstalledBaseReadScope(tenantOfCaller());
    }

    boolean open() {
        return tenantId != null;
    }

    UUID tenantId() {
        return tenantId;
    }

    private static UUID tenantOfCaller() {
        String raw = SecurityUtils.getCurrentUser().map(user -> user.getTenantId()).orElse(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

/** Assets whose subscription tenant equals the caller. A null subscription tenant never matches. */
final class InstalledBaseQueries {
    private InstalledBaseQueries() {
    }

    static Specification<Asset> matching(UUID tenantId, String accountId, Long subscriptionId,
                                         String contractLineId, Long id) {
        return (root, query, cb) -> {
            Subquery<Long> subscriptionIds = query.subquery(Long.class);
            Root<Subscription> subscription = subscriptionIds.from(Subscription.class);
            subscriptionIds.select(subscription.get("id"));
            subscriptionIds.where(cb.equal(subscription.get("tenantId"), tenantId));
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            predicates.add(root.get("subscriptionId").in(subscriptionIds));
            if (id != null) {
                predicates.add(cb.equal(root.get("id"), id));
            }
            if (accountId != null && !accountId.isBlank()) {
                predicates.add(cb.equal(root.get("accountId"), accountId));
            }
            if (subscriptionId != null) {
                predicates.add(cb.equal(root.get("subscriptionId"), subscriptionId));
            }
            if (contractLineId != null && !contractLineId.isBlank()) {
                predicates.add(cb.equal(root.get("contractLineId"), contractLineId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
