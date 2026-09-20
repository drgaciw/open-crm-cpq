package com.opencrm.ai.selection.policy;

import com.opencrm.ai.selection.SelectionTypes.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;

/** Observations are completed canary batches, never commands to replay in-flight tool actions. */
public final class CanaryMonitor {
    private final FilePolicyRepository policies;
    public CanaryMonitor(FilePolicyRepository policies) { this.policies = policies; }

    public record Observation(String policyVersion, BigDecimal measuredQuality, BigDecimal totalSpend,
            int successfulTasks, long p95LatencyMillis) {
        public Observation {
            if (policyVersion == null || policyVersion.isBlank() || measuredQuality == null || totalSpend == null
                    || measuredQuality.signum() < 0 || measuredQuality.compareTo(BigDecimal.ONE) > 0
                    || totalSpend.signum() < 0 || successfulTasks < 0 || p95LatencyMillis < 0)
                throw new IllegalArgumentException("Invalid canary observation");
        }
    }

    public String observe(TaskProfile profile, CatalogSnapshot catalog, Instant now, Observation observed) throws IOException {
        var active = policies.active(profile.id());
        if (active.isEmpty() || !active.get().version().equals(observed.policyVersion())) return "SUPERSEDED_OBSERVATION";
        boolean breach = observed.successfulTasks() == 0 || observed.measuredQuality().compareTo(profile.minQuality()) < 0
                || observed.p95LatencyMillis() > profile.maxP95LatencyMillis()
                || observed.totalSpend().divide(BigDecimal.valueOf(Math.max(1, observed.successfulTasks())), MathContext.DECIMAL128)
                    .compareTo(profile.maxCostPerTask()) > 0;
        if (!breach) return "WITHIN_THRESHOLDS";
        boolean restored = false;
        try {
            policies.rollback(profile.id(), p -> PolicyEligibility.valid(p, profile, catalog, now));
            restored = true;
        } catch (IllegalStateException ignored) {
            // No eligible predecessor: revocation leaves an explicit unavailable state.
        } finally { policies.revoke(observed.policyVersion()); }
        return restored ? "ROLLED_BACK" : "REVOKED_UNAVAILABLE";
    }
}
