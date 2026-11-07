package com.opencrm.contract.domain;

/**
 * ADR §2.2 charge models (DRG-661 / DRG-666). Persisted on {@code subscriptions.subscription_model}.
 */
public enum SubscriptionModel {
    SAAS_RECURRING,
    ONPREM_TRANSACTIONAL,
    ONPREM_CAPACITY
}
