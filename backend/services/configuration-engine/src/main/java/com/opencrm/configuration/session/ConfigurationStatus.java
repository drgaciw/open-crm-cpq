package com.opencrm.configuration.session;

public enum ConfigurationStatus {
    IN_PROGRESS,
    COMPLETED,
    EXPIRED,
    CANCELLED,
    /** FreezeConfiguration (DRG-428 / BC-02-05): valid, immutable, carries a computed {@code configurationHash}. */
    FROZEN
}
