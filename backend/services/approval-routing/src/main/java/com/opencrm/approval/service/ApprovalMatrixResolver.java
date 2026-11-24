package com.opencrm.approval.service;

import com.opencrm.approval.domain.MatrixStepMode;
import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.approval.verdict.PolicyBreach;
import com.opencrm.common.exception.BusinessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns one matrix version plus the breaches on a {@code RequiresApproval} verdict into ordered steps.
 * Serial and parallel are step modes. This is configuration in front of the existing tier
 * ({@link QuoteApprovalTierMapper}, {@link ApprovalAuthority}), not a second routing engine.
 */
public final class ApprovalMatrixResolver {

    private ApprovalMatrixResolver() {
    }

    public record Rule(
            String breachType,
            BigDecimal amountMin,
            BigDecimal amountMax,
            int stepOrder,
            MatrixStepMode stepMode,
            QuoteApprovalTier role) {
    }

    public record ResolvedStep(
            int order,
            MatrixStepMode mode,
            List<QuoteApprovalTier> roles,
            List<String> breachTypes) {
    }

    public record ResolvedRoute(long matrixId, int matrixVersion, List<ResolvedStep> steps) {
    }

    public static ResolvedRoute resolve(long matrixId, int matrixVersion, List<Rule> rules, List<PolicyBreach> breaches) {
        if (rules == null || rules.isEmpty()) {
            throw new BusinessException("Approval matrix has no rules", "EMPTY_APPROVAL_MATRIX");
        }
        if (breaches == null || breaches.isEmpty()) {
            throw new BusinessException("RequiresApproval needs at least one breach", "VERDICT_BREACHES_REQUIRED");
        }

        Map<Integer, StepBuilder> steps = new LinkedHashMap<>();
        for (PolicyBreach breach : breaches) {
            if (breach == null || breach.breachType() == null || breach.breachType().isBlank()) {
                throw new BusinessException("Breach type is required", "INVALID_BREACH");
            }
            if (breach.amount() == null) {
                throw new BusinessException("Breach amount is required to select a matrix band", "BREACH_AMOUNT_REQUIRED");
            }
            String type = breach.breachType().trim().toUpperCase(Locale.ROOT);
            List<Rule> matched = rules.stream()
                    .filter(rule -> type.equals(normalize(rule.breachType())))
                    .filter(rule -> inBand(breach.amount(), rule.amountMin(), rule.amountMax()))
                    .toList();
            if (matched.isEmpty()) {
                throw new BusinessException("No matrix rule routes " + type + " at amount " + breach.amount(),
                        "UNROUTED_BREACH");
            }
            for (Rule rule : matched) {
                StepBuilder step = steps.computeIfAbsent(rule.stepOrder(), StepBuilder::new);
                step.accept(rule, type);
            }
        }

        List<ResolvedStep> ordered = steps.values().stream()
                .sorted(Comparator.comparingInt(StepBuilder::order))
                .map(StepBuilder::build)
                .toList();
        return new ResolvedRoute(matrixId, matrixVersion, List.copyOf(ordered));
    }

    static boolean bandsOverlap(BigDecimal minA, BigDecimal maxA, BigDecimal minB, BigDecimal maxB) {
        BigDecimal start = later(minA, minB);
        BigDecimal end = earlier(maxA, maxB);
        if (start == null || end == null) {
            return true;
        }
        return start.compareTo(end) < 0;
    }

    private static boolean inBand(BigDecimal amount, BigDecimal min, BigDecimal max) {
        if (min != null && amount.compareTo(min) < 0) {
            return false;
        }
        return max == null || amount.compareTo(max) < 0;
    }

    private static BigDecimal later(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.compareTo(right) >= 0 ? left : right;
    }

    private static BigDecimal earlier(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static String normalize(String breachType) {
        return breachType == null ? "" : breachType.trim().toUpperCase(Locale.ROOT);
    }

    private static final class StepBuilder {
        private final int order;
        private MatrixStepMode mode;
        private final Set<QuoteApprovalTier> roles = new LinkedHashSet<>();
        private final Set<String> breachTypes = new LinkedHashSet<>();

        private StepBuilder(int order) {
            this.order = order;
        }

        private int order() {
            return order;
        }

        private void accept(Rule rule, String breachType) {
            if (rule.stepOrder() < 1) {
                throw new BusinessException("Matrix step order must be at least 1", "INVALID_MATRIX_STEP");
            }
            if (rule.stepMode() == null || rule.role() == null) {
                throw new BusinessException("Matrix step mode and role are required", "INVALID_MATRIX_STEP");
            }
            if (mode == null) {
                mode = rule.stepMode();
            } else if (mode != rule.stepMode()) {
                throw new BusinessException("Step " + order + " mixes serial and parallel rules", "MATRIX_STEP_CONFLICT");
            }
            if (mode == MatrixStepMode.SERIAL && !roles.isEmpty() && !roles.contains(rule.role())) {
                throw new BusinessException("Serial step " + order + " has more than one role", "MATRIX_STEP_CONFLICT");
            }
            roles.add(rule.role());
            breachTypes.add(breachType);
        }

        private ResolvedStep build() {
            return new ResolvedStep(order, mode, List.copyOf(roles), List.copyOf(new ArrayList<>(breachTypes)));
        }
    }
}
