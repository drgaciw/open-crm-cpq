package com.opencrm.crm.playbook;

import com.opencrm.common.exception.BusinessException;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Parses {@code completedStepIds}, a comma-separated list of playbook step ids
 * the caller has already finished. Blank input means nothing is completed.
 */
public final class PlaybookCompletedStepIds {

    private PlaybookCompletedStepIds() {
    }

    public static Set<Long> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            long id = parseId(trimmed);
            ids.add(id);
        }
        return Set.copyOf(ids);
    }

    private static long parseId(String token) {
        try {
            long id = Long.parseLong(token);
            if (id <= 0) {
                throw new BusinessException(
                        "Completed step ids must be positive",
                        "PLAYBOOK_COMPLETED_STEP_ID");
            }
            return id;
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    "Completed step ids must be integers",
                    "PLAYBOOK_COMPLETED_STEP_ID");
        }
    }
}
