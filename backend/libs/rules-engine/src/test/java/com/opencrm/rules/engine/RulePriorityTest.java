package com.opencrm.rules.engine;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RulePriorityTest {

    @Test
    void minPriorityShouldBeLessThanMaxPriority() {
        for (RulePriority priority : RulePriority.values()) {
            assertThat(priority.getMinPriority())
                    .as("Priority %s min should be less than max", priority.name())
                    .isLessThan(priority.getMaxPriority());
        }
    }

    @Test
    void priorityRangesShouldNotOverlap() {
        // Sort by minPriority ascending
        List<RulePriority> sortedPriorities = Arrays.stream(RulePriority.values())
                .sorted(Comparator.comparingInt(RulePriority::getMinPriority))
                .toList();

        for (int i = 0; i < sortedPriorities.size() - 1; i++) {
            RulePriority current = sortedPriorities.get(i);
            RulePriority next = sortedPriorities.get(i + 1);

            assertThat(current.getMaxPriority())
                    .as("Priority range %s should end before %s begins", current.name(), next.name())
                    .isLessThan(next.getMinPriority());
        }
    }

    @Test
    void expectedValuesExist() {
        assertThat(RulePriority.values())
                .containsExactly(
                        RulePriority.POLICY_GUARDRAIL,
                        RulePriority.CONFIG_VALIDATION,
                        RulePriority.LINE_PRICING,
                        RulePriority.QUOTE_ADJUSTMENT,
                        RulePriority.APPROVAL_WORKFLOW,
                        RulePriority.EXPLAINABILITY
                );
    }
}
