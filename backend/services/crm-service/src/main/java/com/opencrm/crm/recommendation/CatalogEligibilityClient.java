package com.opencrm.crm.recommendation;

import java.util.Collection;
import java.util.Set;

/**
 * Optional catalog eligibility reader. No bean is registered in this package: CRM does not call
 * product-service, and this type has no HTTP client.
 *
 * <p>A future in-process adapter may expose a bean. {@link PermissiveEligibilityGate} will then drop
 * only the ids that adapter marks ineligible.
 */
public interface CatalogEligibilityClient {

    Set<String> ineligibleProductIds(RecommendationSubject subject, Collection<String> candidateProductIds);
}
