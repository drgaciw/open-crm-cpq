package com.opencrm.contract.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Resolves the interim Entra {@code tid} for {@code subscriptions.tenant_id} (DRG-662).
 * Stub: RED tests drive the real behaviour.
 */
public final class SubscriptionTenant {

    public static final String INVALID_METRIC = "opencrm.subscription.tenant.invalid";

    private static final Logger log = LoggerFactory.getLogger(SubscriptionTenant.class);

    private SubscriptionTenant() {
    }

    /** RED stub: always null. */
    public static UUID resolve(String provenanceTenantId, MeterRegistry meters) {
        return null;
    }
}
