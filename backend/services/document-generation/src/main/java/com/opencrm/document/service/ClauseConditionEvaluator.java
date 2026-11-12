package com.opencrm.document.service;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.document.dto.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Component
public class ClauseConditionEvaluator {
    private static final int MAX_DEPTH = 12;
    private static final int MAX_NODES = 100;

    public void validate(ClauseCondition condition) { validateNode(condition, 0, new int[]{0}); }

    private void validateNode(ClauseCondition c, int depth, int[] count) {
        if (c == null) return;
        if (depth > MAX_DEPTH || ++count[0] > MAX_NODES || c.operator() == null)
            throw invalid();
        boolean group = c.operator() == ClauseCondition.Operator.AND || c.operator() == ClauseCondition.Operator.OR;
        if (group) {
            if (c.field() != null || c.value() != null || c.children() == null || c.children().isEmpty()) throw invalid();
            for (ClauseCondition child : c.children()) {
                if (child == null) throw invalid();
                validateNode(child, depth + 1, count);
            }
            return;
        }
        if (c.children() != null && !c.children().isEmpty()) throw invalid();
        if (c.field() == null || !(c.field().equals("region") || c.field().equals("segment")
                || c.field().matches("quoteAttributes[.][A-Za-z0-9_.-]{1,100}"))) throw invalid();
        switch (c.operator()) {
            case EXISTS -> { if (c.value() != null) throw invalid(); }
            case IN -> {
                if (!(c.value() instanceof List<?> list) || list.isEmpty() || list.size() > 100
                        || list.stream().anyMatch(v -> !scalar(v))) throw invalid();
            }
            case GT, GTE, LT, LTE -> { if (!(c.value() instanceof Number) || number(c.value()) == null) throw invalid(); }
            case EQUALS -> { if (!scalar(c.value())) throw invalid(); }
            default -> throw invalid();
        }
    }

    public boolean matches(ClauseCondition c, ClauseSelectionContext context) {
        validate(c);
        if (context == null) throw new BusinessException("Selection context is required");
        return evaluate(c, context);
    }

    private boolean evaluate(ClauseCondition c, ClauseSelectionContext context) {
        if (c == null) return true;
        if (c.operator() == ClauseCondition.Operator.AND) return c.children().stream().allMatch(child -> evaluate(child, context));
        if (c.operator() == ClauseCondition.Operator.OR) return c.children().stream().anyMatch(child -> evaluate(child, context));
        Object actual = switch (c.field()) {
            case "region" -> context.region();
            case "segment" -> context.segment();
            default -> context.quoteAttributes().get(c.field().substring("quoteAttributes.".length()));
        };
        if (c.operator() == ClauseCondition.Operator.EXISTS) return actual != null;
        if (actual == null) return false;
        return switch (c.operator()) {
            case EQUALS -> equal(actual, c.value());
            case IN -> ((List<?>) c.value()).stream().anyMatch(v -> equal(actual, v));
            case GT, GTE, LT, LTE -> {
                BigDecimal a = number(actual), b = number(c.value());
                if (a == null || b == null) yield false;
                int result = a.compareTo(b);
                yield switch (c.operator()) {
                    case GT -> result > 0; case GTE -> result >= 0;
                    case LT -> result < 0; case LTE -> result <= 0;
                    default -> false;
                };
            }
            default -> false;
        };
    }
    private static boolean equal(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            BigDecimal x = number(a), y = number(b);
            return x != null && y != null && x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }
    private static boolean scalar(Object v) {
        return v instanceof String s ? s.length() <= 1000 : v instanceof Boolean || v instanceof Number && number(v) != null;
    }
    private static BigDecimal number(Object v) {
        if (!(v instanceof Number)) return null;
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException ex) { return null; }
    }
    private static BusinessException invalid() { return new BusinessException("Invalid or oversized structured clause condition", "INVALID_CLAUSE_CONDITION"); }
}
