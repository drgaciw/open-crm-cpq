package com.opencrm.crm.fact;

import java.util.Arrays;
import java.util.List;

/**
 * Lifecycle of one fact row.
 * <p>
 * {@link #PROVENANCE} is the POSSIBLE band: stored, never listed as a value.
 * {@link #UNRESOLVED} is contradicting evidence, kept regardless of score.
 * Both are required by ADR-028 and sit beside the story's PROPOSED / APPLIED / DISMISSED / SUPERSEDED.
 */
public enum FactStatus {
    PROPOSED,
    APPLIED,
    DISMISSED,
    SUPERSEDED,
    UNRESOLVED,
    PROVENANCE;

    public boolean shownInInbox() {
        return switch (this) {
            case PROPOSED, UNRESOLVED -> true;
            case APPLIED, DISMISSED, SUPERSEDED, PROVENANCE -> false;
        };
    }

    public static List<FactStatus> inbox() {
        return Arrays.stream(values()).filter(FactStatus::shownInInbox).toList();
    }
}
