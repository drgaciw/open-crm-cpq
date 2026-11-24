package com.opencrm.approval.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Next approver tier after an SLA breach (DRG-444).
 *
 * <p>DRG-443's approval matrix is the source of a configured path: {@code SubmitForApproval} copies that
 * path onto the request ({@code escalationPath}, comma-separated tiers). When the request has no path yet,
 * the default chain above the current tier is used so a decision can still escalate.
 */
public final class EscalationPath {

    private EscalationPath() {
    }

    public static Optional<String> next(String currentTier, String configuredPath) {
        String current = normalize(currentTier);
        if (current == null) {
            return Optional.empty();
        }
        List<String> steps = configuredPath == null || configuredPath.isBlank()
                ? defaultSteps(current)
                : parse(configuredPath);
        int index = steps.indexOf(current);
        if (index < 0 || index + 1 >= steps.size()) {
            return Optional.empty();
        }
        return Optional.of(steps.get(index + 1));
    }

    private static List<String> defaultSteps(String current) {
        return switch (current) {
            case "DEAL_DESK", "MANAGER" -> List.of(current, "SALES_VP", "CFO");
            case "DIRECTOR", "VP_SALES", "SALES_VP" -> List.of(current, "CFO");
            case "CFO" -> List.of("CFO");
            default -> List.of(current);
        };
    }

    private static List<String> parse(String configuredPath) {
        List<String> steps = new ArrayList<>();
        for (String part : configuredPath.split(",")) {
            String tier = normalize(part);
            if (tier != null) {
                steps.add(tier);
            }
        }
        return List.copyOf(steps);
    }

    private static String normalize(String tier) {
        if (tier == null || tier.isBlank()) {
            return null;
        }
        return tier.trim().toUpperCase(Locale.ROOT);
    }
}
