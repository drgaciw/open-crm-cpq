package com.opencrm.ai.experiment;

/**
 * Apply winning variant as a DRAFT rule. Requires DEAL_DESK or ADMIN.
 * Actor is recorded from {@code CurrentActor} (ADR-016 D6), never from the request body.
 */
public record ApplyExperimentRequest() {
}
