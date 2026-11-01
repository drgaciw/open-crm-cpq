package com.opencrm.crm.territory;

import org.springframework.stereotype.Component;

/**
 * Territory operations remain unavailable until authorization/scope, durable invalidation and
 * unassignment delivery are implemented and verified. There is no runtime release flag.
 */
@Component
public final class TerritoryAssignmentReadiness {
    public boolean isReady() {
        return false;
    }
}
