package com.opencrm.pricing.product;

/**
 * Product type classification - comprehensive list from PRD.
 */
public enum ProductType {
    // SaaS product types
    SAAS_SEAT_NAMED,
    SAAS_SEAT_CONCURRENT,
    SAAS_MAU,
    SAAS_USAGE,
    SAAS_CREDITS,
    SAAS_TIERED,
    SAAS_BASE_OVERAGE,
    SAAS_COMMITTED,
    SAAS_OUTCOME,

    // On-premise product types
    ONPREM_PERPETUAL,
    ONPREM_CORE,
    ONPREM_SERVER,
    ONPREM_CAPACITY,
    ONPREM_SITE,
    ONPREM_MODULE,
    ONPREM_MIGRATION,
    ONPREM_SUPPORT,

    // Hybrid product types
    HYBRID_BUNDLE,
    HYBRID_BRIDGE,
    HYBRID_MIGRATION_RAMP
}
