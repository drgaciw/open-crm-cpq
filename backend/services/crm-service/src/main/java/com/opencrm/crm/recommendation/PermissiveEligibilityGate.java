package com.opencrm.crm.recommendation;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Default {@link EligibilityGate}. When no {@link CatalogEligibilityClient} bean is configured, every
 * candidate stays eligible. When a client is present, only ids that client marks are rejected — nothing extra.
 */
@Component
@RequiredArgsConstructor
public class PermissiveEligibilityGate implements EligibilityGate {

    private final ObjectProvider<CatalogEligibilityClient> catalogClients;

    @Override
    public Set<String> ineligibleProductIds(RecommendationSubject subject, Collection<String> candidateProductIds) {
        if (candidateProductIds == null || candidateProductIds.isEmpty()) {
            return Set.of();
        }
        CatalogEligibilityClient client = catalogClients.getIfAvailable();
        if (client == null) {
            return Set.of();
        }
        Set<String> marked = client.ineligibleProductIds(subject, candidateProductIds);
        if (marked == null || marked.isEmpty()) {
            return Set.of();
        }
        Set<String> candidates = new HashSet<>();
        for (String id : candidateProductIds) {
            if (id != null) {
                candidates.add(id);
            }
        }
        Set<String> rejected = new LinkedHashSet<>();
        for (String id : marked) {
            if (id != null && candidates.contains(id)) {
                rejected.add(id);
            }
        }
        return Set.copyOf(rejected);
    }
}
