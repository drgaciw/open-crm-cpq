package com.opencrm.approval.testdoubles;

import java.util.List;

/**
 * Ordered escalation steps. {@link #current()} is the first step. {@link #nextAfter(String)} is the following step.
 * Feature moves this onto the production escalation type.
 */
public record EscalationPath(List<String> steps) {

    public EscalationPath {
        if (steps == null || steps.size() < 2 || steps.stream().anyMatch(step -> step == null || step.isBlank())) {
            throw new IllegalArgumentException("EscalationPath needs at least two named steps");
        }
        steps = List.copyOf(steps);
    }

    public String current() {
        return steps.getFirst();
    }

    public String nextAfter(String assigneeId) {
        int at = steps.indexOf(assigneeId);
        if (at < 0 || at + 1 >= steps.size()) {
            throw new IllegalArgumentException("No next step after " + assigneeId);
        }
        return steps.get(at + 1);
    }
}
