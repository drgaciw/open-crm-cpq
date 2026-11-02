package com.opencrm.crm.recommendation;

import java.util.Collection;
import java.util.Set;

/**
 * BC-01 eligibility port for advisory product suggestions.
 *
 * <p>The gate only reports product ids that must be dropped. It does not change deal stage, quotes,
 * or eligibility configuration. {@link PermissiveEligibilityGate} is the default bean: with no
 * {@link CatalogEligibilityClient} it rejects nothing extra.
 */
public interface EligibilityGate {

    /**
     * @param subject deal facts; never a mutable deal
     * @param candidateProductIds ids the ranker is about to return
     * @return ids among {@code candidateProductIds} that are ineligible; never ids the caller did not ask about
     */
    Set<String> ineligibleProductIds(RecommendationSubject subject, Collection<String> candidateProductIds);
}
