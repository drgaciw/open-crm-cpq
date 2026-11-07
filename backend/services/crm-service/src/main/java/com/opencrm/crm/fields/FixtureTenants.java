package com.opencrm.crm.fields;

import com.opencrm.common.exception.ResourceNotFoundException;

import java.util.Set;

/**
 * The only tenants this slice accepts. They are literals for tests and local calls.
 * Production tenant ids, JWT {@code tid}, and ADR-036 account ownership are not read.
 */
public final class FixtureTenants {

    public static final String A = "fixture-tenant-a";
    public static final String B = "fixture-tenant-b";

    private static final Set<String> ALLOWED = Set.of(A, B);

    private FixtureTenants() {
    }

    public static void require(String tenant) {
        if (tenant == null || !ALLOWED.contains(tenant)) {
            throw new ResourceNotFoundException("Field tenant", tenant == null ? "missing" : tenant);
        }
    }
}
