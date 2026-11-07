package com.opencrm.contract.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Resolves the interim Entra {@code tid} for {@code subscriptions.tenant_id} (DRG-662).
 *
 * <p>tenantId is the Entra {@code tid} claim: a per-deployment value
 * (single issuer per deployment, ADR-016 / DRG-632), deliberately weak interim.
 * It is NOT a real per-tenant id.
 */
public final class SubscriptionTenant {

    public static final String INVALID_METRIC = "opencrm.subscription.tenant.invalid";

    private static final Logger log = LoggerFactory.getLogger(SubscriptionTenant.class);

    private SubscriptionTenant() {
    }

    /**
     * Parses the DRG-631 provenance string. Absent or non-GUID → null, warn, and increment
     * {@link #INVALID_METRIC}. Never rejects the event.
     */
    public static UUID resolve(String provenanceTenantId, MeterRegistry meters) {
        if (provenanceTenantId == null || provenanceTenantId.isBlank()) {
            warnAndCount(meters, "absent");
            return null;
        }
        String trimmed = provenanceTenantId.trim();
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ex) {
            warnAndCount(meters, "non-guid");
            return null;
        }
    }

    private static void warnAndCount(MeterRegistry meters, String reason) {
        log.warn("subscription tenant provenance {} — storing NULL (interim Entra tid; DRG-662)", reason);
        if (meters != null) {
            meters.counter(INVALID_METRIC).increment();
        }
    }
}
