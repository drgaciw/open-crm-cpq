package com.opencrm.crm.fields;

import com.opencrm.common.exception.ResourceNotFoundException;

import java.util.Set;

/**
 * Fixture tenant ids for field-registry tests and local samples.
 * Fact-ledger routes derive tenant from the verified JWT {@code tid}
 * ({@code FactTenantResolver}); they do not trust these literals from a query param.
 * ADR-036 account→tenant ownership is still Proposed.
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
