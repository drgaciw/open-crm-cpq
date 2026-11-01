package com.opencrm.quote.lifecycle;

/** Reads whether a target subscription is ACTIVE. Implementations do not price. */
public interface SubscriptionTargetReader {

    SubscriptionTarget read(String targetSubscriptionId);
}
