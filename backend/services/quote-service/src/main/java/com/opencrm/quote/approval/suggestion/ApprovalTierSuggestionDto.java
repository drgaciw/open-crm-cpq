package com.opencrm.quote.approval.suggestion;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Advisory tier suggestion (ADR-015). Mirrors the frontend {@code JevSuggestionEnvelope}. The user still
 * picks the tier; this never preselects or submits one.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalTierSuggestionDto(Status status, Suggestion suggestion) {

    public enum Status {
        /** A suggestion is available for the quote as it currently stands. */
        READY,
        /** An evaluation was started; ask again shortly. */
        PENDING,
        /** Disabled, Jev unsure or failing, or the quote does not exist. Nothing to show. */
        UNAVAILABLE
    }

    /** @param advisory always true */
    public record Suggestion(String value, double confidence, Map<String, Double> probabilities,
                             String rubricVersion, String model, boolean advisory, Instant computedAt) {
    }

    static ApprovalTierSuggestionDto unavailable() {
        return new ApprovalTierSuggestionDto(Status.UNAVAILABLE, null);
    }

    static ApprovalTierSuggestionDto pending() {
        return new ApprovalTierSuggestionDto(Status.PENDING, null);
    }
}
