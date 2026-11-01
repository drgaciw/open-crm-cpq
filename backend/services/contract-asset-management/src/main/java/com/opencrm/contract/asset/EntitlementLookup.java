package com.opencrm.contract.asset;

import java.util.Optional;

/**
 * Resolves an entitlement for a contract line when one exists.
 * The production lookup is empty until entitlements are available (DRG-700).
 */
@FunctionalInterface
public interface EntitlementLookup {

    Optional<String> findEntitlementId(String contractLineId);
}
