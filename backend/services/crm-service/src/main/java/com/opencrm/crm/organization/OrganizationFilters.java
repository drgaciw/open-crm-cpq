package com.opencrm.crm.organization;

import org.springframework.data.jpa.domain.Specification;
import java.util.Locale;

/** Database filters are applied before paging so totals and pages describe the same result set. */
final class OrganizationFilters {
    private OrganizationFilters() {}

    static Specification<Organization> matching(String query, OrganizationType type, String industry,
                                                 String territory, String ownerId) {
        return (root, ignored, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (present(query)) predicates.add(cb.like(cb.lower(root.get("name")), pattern(query), '\\'));
            if (present(industry)) predicates.add(cb.equal(cb.lower(root.get("industry")), normalized(industry)));
            if (present(territory)) predicates.add(cb.like(cb.lower(root.get("territory")), pattern(territory), '\\'));
            if (present(ownerId)) predicates.add(cb.like(cb.lower(root.get("ownerId")), pattern(ownerId), '\\'));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static String normalized(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private static String pattern(String value) {
        return "%" + normalized(value).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
