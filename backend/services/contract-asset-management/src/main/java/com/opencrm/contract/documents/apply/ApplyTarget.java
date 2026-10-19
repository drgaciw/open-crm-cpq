package com.opencrm.contract.documents.apply;

/** Subscription identity resolved from the unit target before the row lock. */
public record ApplyTarget(long id, String uuid, long commercialRevision) {
}
