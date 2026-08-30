package com.opencrm.configuration.constraint;

import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.dto.ConfigurationViolation;
import com.opencrm.configuration.evaluate.ProductRuleEvaluationCore;
import com.opencrm.configuration.product.ProductConfigurationRuleClient;
import com.opencrm.configuration.session.ConfigurationSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Session constraint solver: maps {@link ConfigurationSession} selections to product IDs
 * and evaluates product-service rules via {@link ProductRuleEvaluationCore} (S2B-8 / Wave G2).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConstraintSolver {

    private final ProductConfigurationRuleClient ruleClient;

    /**
     * Full evaluation including soft RECOMMENDS violations ({@code valid} still true when only soft).
     * Passes {@link ConfigurationSession#getSelections()} as attribute bag for VALIDATES
     * {@code conditionExpression} (Wave H1).
     */
    public ConfigurationValidationResult evaluate(ConfigurationSession session) {
        List<Long> selectedIds = selectedProductIds(session);
        Map<String, String> attributes = sessionAttributes(session);
        ConfigurationValidationResult result = ProductRuleEvaluationCore.evaluate(
                selectedIds,
                ruleClient::getRulesForProduct,
                attributes);
        log.debug("Evaluated configuration session {} selectedIds={} attrs={} valid={} violations={}",
                session.getId(),
                selectedIds,
                attributes.size(),
                result.isValid(),
                result.getViolations() == null ? 0 : result.getViolations().size());
        return result;
    }

    /**
     * Session selection map used as VALIDATES attribute bag (option key → value).
     */
    static Map<String, String> sessionAttributes(ConfigurationSession session) {
        if (session == null || session.getSelections() == null || session.getSelections().isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(session.getSelections());
    }

    /**
     * Hard-failure messages only (empty ⇒ hard-valid). Soft RECOMMENDS are omitted so callers
     * that treat any message as blocking stay compatible; use {@link #evaluate} for soft warnings.
     */
    public List<String> validate(ConfigurationSession session) {
        return hardFailureMessages(evaluate(session));
    }

    /**
     * Check if a specific selection is compatible with current configuration (hard rules only).
     */
    public boolean isCompatible(ConfigurationSession session, String optionKey, String optionValue) {
        Map<String, String> tempSelections = new HashMap<>(
                session.getSelections() == null ? Map.of() : session.getSelections());
        tempSelections.put(optionKey, optionValue);

        ConfigurationSession temp = ConfigurationSession.builder()
                .productId(session.getProductId())
                .userId(session.getUserId())
                .status(session.getStatus())
                .selections(tempSelections)
                .valid(false)
                .build();
        return evaluate(temp).isValid();
    }

    /**
     * Project session productId (if numeric) plus selection values that parse as {@link Long}
     * into the selected product ID list for rule evaluation.
     */
    static List<Long> selectedProductIds(ConfigurationSession session) {
        Set<Long> ids = new LinkedHashSet<>();
        if (session == null) {
            return List.of();
        }
        parseLong(session.getProductId()).ifPresent(ids::add);
        Map<String, String> selections = session.getSelections();
        if (selections != null) {
            for (String value : selections.values()) {
                parseLong(value).ifPresent(ids::add);
            }
        }
        return List.copyOf(ids);
    }

    static List<String> hardFailureMessages(ConfigurationValidationResult result) {
        if (result == null || result.getViolations() == null || result.getViolations().isEmpty()) {
            return List.of();
        }
        List<String> messages = new ArrayList<>();
        for (ConfigurationViolation v : result.getViolations()) {
            if (v == null || !ProductRuleEvaluationCore.isHardFailure(v.getRuleType())) {
                continue;
            }
            messages.add(formatViolation(v));
        }
        return messages;
    }

    public static List<String> allViolationMessages(ConfigurationValidationResult result) {
        if (result == null || result.getViolations() == null || result.getViolations().isEmpty()) {
            return List.of();
        }
        List<String> messages = new ArrayList<>();
        for (ConfigurationViolation v : result.getViolations()) {
            if (v == null) {
                continue;
            }
            messages.add(formatViolation(v));
        }
        return messages;
    }

    private static String formatViolation(ConfigurationViolation v) {
        if (v.getDescription() != null && !v.getDescription().isBlank()) {
            return v.getDescription();
        }
        return v.getRuleType() == null ? "Configuration rule violation" : v.getRuleType() + " violation";
    }

    private static java.util.Optional<Long> parseLong(String raw) {
        if (raw == null) {
            return java.util.Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Long.parseLong(trimmed));
        } catch (NumberFormatException ex) {
            return java.util.Optional.empty();
        }
    }
}
