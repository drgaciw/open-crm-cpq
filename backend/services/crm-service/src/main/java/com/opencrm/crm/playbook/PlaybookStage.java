package com.opencrm.crm.playbook;

import com.opencrm.common.exception.BusinessException;

/**
 * Pipeline stage stored on a playbook step. The names are the {@code DealStage}
 * names. This type lives in the playbook package so playbook steps do not depend
 * on the deal type, and it has no buying-role dimension.
 */
public enum PlaybookStage {
    PROSPECTING,
    QUALIFICATION,
    NEEDS_ANALYSIS,
    PROPOSAL,
    NEGOTIATION,
    WON,
    LOST;

    /**
     * Value persisted and returned on the API. The switch is exhaustive so a new
     * constant does not compile until it has an explicit DealStage name.
     */
    public String storedKey() {
        return switch (this) {
            case PROSPECTING -> "PROSPECTING";
            case QUALIFICATION -> "QUALIFICATION";
            case NEEDS_ANALYSIS -> "NEEDS_ANALYSIS";
            case PROPOSAL -> "PROPOSAL";
            case NEGOTIATION -> "NEGOTIATION";
            case WON -> "WON";
            case LOST -> "LOST";
        };
    }

    /**
     * Parses a stage query or request value. Blank and unknown keys are rejected.
     * Matching is the stored DealStage name, not a case-insensitive alias.
     */
    public static PlaybookStage fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("Stage is required", "PLAYBOOK_STAGE_REQUIRED");
        }
        String key = raw.trim();
        for (PlaybookStage stage : values()) {
            if (stage.storedKey().equals(key)) {
                return stage;
            }
        }
        throw new BusinessException("Unknown playbook stage: " + key, "PLAYBOOK_STAGE_UNKNOWN");
    }
}
