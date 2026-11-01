package com.opencrm.quote.lifecycle;

/** Status facts read for V2. The id is the seller's target reference. */
public record SubscriptionTarget(String subscriptionId, String status, Long commercialRevision) {
}
