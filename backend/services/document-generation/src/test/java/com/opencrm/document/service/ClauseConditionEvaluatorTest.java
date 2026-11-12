package com.opencrm.document.service;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.document.dto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.util.*;
import static com.opencrm.document.dto.ClauseCondition.Operator.*;
import static org.junit.jupiter.api.Assertions.*;

class ClauseConditionEvaluatorTest {
    private final ClauseConditionEvaluator evaluator = new ClauseConditionEvaluator();
    private final ClauseSelectionContext context = new ClauseSelectionContext("EU", "Enterprise",
            Map.of("total", new BigDecimal("100.00"), "active", true, "literal.key", "value", "stringNumber", "100"));
    private ClauseCondition leaf(ClauseCondition.Operator op, String field, Object value) {
        return new ClauseCondition(op, field, value, null);
    }
    @Test void unconditionalMatches() { assertTrue(evaluator.matches(null, context)); }
    @Test void regionSegmentAndAttributePredicates() {
        assertTrue(evaluator.matches(new ClauseCondition(AND, null, null, List.of(
                leaf(EQUALS, "region", "EU"), leaf(IN, "segment", List.of("SMB", "Enterprise")),
                leaf(EQUALS, "quoteAttributes.active", true))), context));
    }
    @Test void orAndShortCircuitRemainDeterministic() {
        ClauseCondition c = new ClauseCondition(OR, null, null, List.of(leaf(EQUALS, "region", "US"), leaf(EQUALS, "segment", "Enterprise")));
        assertTrue(evaluator.matches(c, context));
        assertFalse(evaluator.matches(leaf(EQUALS, "region", "US"), context));
    }
    @ParameterizedTest @CsvSource({"GT,99,true", "GT,100,false", "GTE,100,true", "LT,101,true", "LT,100,false", "LTE,100,true", "LTE,99,false"})
    void numericComparisons(ClauseCondition.Operator op, int threshold, boolean expected) {
        assertEquals(expected, evaluator.matches(leaf(op, "quoteAttributes.total", threshold), context));
    }
    @Test void numericEqualityIgnoresScaleButDoesNotCoerceStrings() {
        assertTrue(evaluator.matches(leaf(EQUALS, "quoteAttributes.total", 100), context));
        assertFalse(evaluator.matches(leaf(GTE, "quoteAttributes.stringNumber", 99), context));
        assertFalse(evaluator.matches(leaf(EQUALS, "quoteAttributes.total", "100"), context));
    }
    @Test void existsAndMissingAttributes() {
        assertTrue(evaluator.matches(leaf(EXISTS, "quoteAttributes.active", null), context));
        assertFalse(evaluator.matches(leaf(EXISTS, "quoteAttributes.missing", null), context));
        assertFalse(evaluator.matches(leaf(EQUALS, "quoteAttributes.missing", "x"), context));
        assertTrue(evaluator.matches(leaf(EQUALS, "quoteAttributes.literal.key", "value"), context));
    }
    @Test void expressionsCannotBeExecutedOrTraversed() {
        assertThrows(BusinessException.class, () -> evaluator.matches(leaf(EQUALS, "T(java.lang.Runtime).getRuntime()", "x"), context));
        assertThrows(BusinessException.class, () -> evaluator.matches(leaf(EQUALS, "quoteAttributes['total']", 100), context));
        assertFalse(evaluator.matches(leaf(EXISTS, "quoteAttributes.class", null), context));
    }
    @Test void invalidShapeAndOperandTypesAreRejected() {
        assertThrows(BusinessException.class, () -> evaluator.validate(leaf(GT, "region", "100")));
        assertThrows(BusinessException.class, () -> evaluator.validate(leaf(IN, "region", "EU")));
        assertThrows(BusinessException.class, () -> evaluator.validate(leaf(EQUALS, "region", Map.of())));
        assertThrows(BusinessException.class, () -> evaluator.validate(new ClauseCondition(AND, null, null, List.of())));
        assertThrows(BusinessException.class, () -> evaluator.validate(new ClauseCondition(null, "region", "EU", null)));
        assertThrows(BusinessException.class, () -> evaluator.validate(leaf(GT, "region", Double.NaN)));
    }
    @Test void oversizedTreesAndMembershipAreRejected() {
        ClauseCondition deep = leaf(EXISTS, "region", null);
        for (int i = 0; i < 14; i++) deep = new ClauseCondition(AND, null, null, List.of(deep));
        ClauseCondition finalDeep = deep;
        assertThrows(BusinessException.class, () -> evaluator.validate(finalDeep));
        assertThrows(BusinessException.class, () -> evaluator.validate(new ClauseCondition(OR, null, null,
                Collections.nCopies(101, leaf(EXISTS, "region", null)))));
        assertThrows(BusinessException.class, () -> evaluator.validate(leaf(IN, "region", Collections.nCopies(101, "EU"))));
    }
}
