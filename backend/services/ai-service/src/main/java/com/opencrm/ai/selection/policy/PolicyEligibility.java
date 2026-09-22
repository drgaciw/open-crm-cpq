package com.opencrm.ai.selection.policy;

import com.opencrm.ai.selection.SelectionTypes.*;
import com.opencrm.ai.selection.SelectionEngine;
import java.math.BigDecimal;
import java.time.Instant;

/** Runtime checks deliberately repeat qualification against the current profile and catalog. */
public final class PolicyEligibility {
    private PolicyEligibility() { }

    public static boolean valid(SelectionPolicy policy, TaskProfile profile, CatalogSnapshot catalog, Instant now) {
        return policy.profileId().equals(profile.id()) && policy.profileVersion().equals(profile.version())
                && policy.catalogVersion().equals(catalog.version())
                && !now.isBefore(policy.effectiveAt()) && now.isBefore(policy.expiresAt())
                && !catalog.fetchedAt().isAfter(now) && now.isBefore(catalog.expiresAt())
                && now.isBefore(catalog.fetchedAt().plus(profile.evidenceTtl()))
                && !policy.expiresAt().isAfter(policy.effectiveAt().plus(profile.policyTtl()))
                && policy.routes().stream().allMatch(c -> eligible(c, profile, catalog, now));
    }

    public static boolean eligible(QualifiedCandidate candidate, TaskProfile profile, CatalogSnapshot catalog, Instant now) {
        Endpoint endpoint = candidate.endpoint();
        return SelectionEngine.eligible(endpoint, profile, catalog, now)
                && rate(candidate.expectedCostPerSuccess())
                && candidate.expectedCostPerSuccess().compareTo(profile.maxCostPerTask()) <= 0
                && candidate.quality() != null && candidate.quality().compareTo(profile.minQuality()) >= 0
                && candidate.quality().compareTo(BigDecimal.ONE) <= 0
                && candidate.confidence() != null && candidate.confidence().compareTo(profile.minConfidence()) >= 0
                && candidate.confidence().compareTo(BigDecimal.ONE) <= 0
                && candidate.p95LatencyMillis() >= 0 && candidate.p95LatencyMillis() <= profile.maxP95LatencyMillis()
                && candidate.benchmarkVersion() != null && !candidate.benchmarkVersion().isBlank();
    }

    private static boolean rate(BigDecimal value) { return value != null && value.signum() >= 0; }
}
