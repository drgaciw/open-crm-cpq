package com.opencrm.crm.recommendation;

import com.opencrm.crm.deal.DealType;

/**
 * Read-only facts the eligibility gate may see. The deal entity is not passed through, so a gate
 * cannot change stage, amount, or any other persisted field.
 */
public record RecommendationSubject(Long dealId, Long organizationId, String industry, DealType dealType) {
}
