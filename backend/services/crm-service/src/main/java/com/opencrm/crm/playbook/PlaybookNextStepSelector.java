package com.opencrm.crm.playbook;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Chooses the next incomplete step for one stage.
 * <p>
 * "Next" is the incomplete step with the lowest {@code sortOrder} in that stage,
 * then the lowest id. A completed id removes that step only. Steps from any other
 * stage are ignored, including when their ids are present in the completed set.
 * An empty result means the stage has no remaining step.
 */
public final class PlaybookNextStepSelector {

    private PlaybookNextStepSelector() {
    }

    public static Optional<PlaybookStep> next(List<PlaybookStep> steps, PlaybookStage stage, Set<Long> completedStepIds) {
        if (stage == null) {
            throw new IllegalArgumentException("stage is required");
        }
        Set<Long> completed = completedStepIds == null ? Set.of() : completedStepIds;
        List<PlaybookStep> candidates = steps == null ? List.of() : steps;
        return candidates.stream()
                .filter(step -> step != null && !step.isDeleted())
                .filter(step -> stage == step.getStage())
                .filter(step -> step.getId() == null || !completed.contains(step.getId()))
                .min(Comparator.comparingInt(PlaybookStep::getSortOrder)
                        .thenComparing(PlaybookStep::getId, Comparator.nullsLast(Comparator.naturalOrder())));
    }
}
