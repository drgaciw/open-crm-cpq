package com.opencrm.contract.asset;

import org.springframework.stereotype.Component;

import java.util.Optional;

/** Nothing to link yet. entitlementId stays empty (DRG-654 owner decision, follow-up DRG-700). */
@Component
public class EmptyEntitlementLookup implements EntitlementLookup {

    @Override
    public Optional<String> findEntitlementId(String contractLineId) {
        return Optional.empty();
    }
}
