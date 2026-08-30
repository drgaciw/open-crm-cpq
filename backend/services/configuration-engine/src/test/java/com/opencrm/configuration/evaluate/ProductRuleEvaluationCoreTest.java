package com.opencrm.configuration.evaluate;

import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.configuration.product.ProductConfigurationRuleDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductRuleEvaluationCore VALIDATES conditionExpression (Wave H1)")
class ProductRuleEvaluationCoreTest {

    @Test
    @DisplayName("attr:key=value matches when attribute equals expected")
    void attrEqualsMatches() {
        assertThat(ProductRuleEvaluationCore.matchesAttrExpression(
                "attr:color=red", Map.of("color", "red"))).isTrue();
        assertThat(ProductRuleEvaluationCore.matchesAttrExpression(
                "attr:color=red", Map.of("color", "blue"))).isFalse();
        assertThat(ProductRuleEvaluationCore.matchesAttrExpression(
                "attr:color=red", Map.of("size", "L"))).isFalse();
    }

    @Test
    @DisplayName("attr:key!=value matches when attribute differs or missing")
    void attrNotEqualsMatches() {
        assertThat(ProductRuleEvaluationCore.matchesAttrExpression(
                "attr:color!=red", Map.of("color", "blue"))).isTrue();
        assertThat(ProductRuleEvaluationCore.matchesAttrExpression(
                "attr:color!=red", Map.of("color", "red"))).isFalse();
        assertThat(ProductRuleEvaluationCore.matchesAttrExpression(
                "attr:color!=red", Map.of())).isTrue();
    }

    @Test
    @DisplayName("unsupported dialect returns null (skip)")
    void unsupportedDialectIsNull() {
        assertThat(ProductRuleEvaluationCore.matchesAttrExpression(
                "spel:color=='red'", Map.of("color", "red"))).isNull();
        assertThat(ProductRuleEvaluationCore.matchesAttrExpression(
                "color=red", Map.of("color", "red"))).isNull();
    }

    @Test
    @DisplayName("expression-only VALIDATES hard-fails when attrs mismatch")
    void expressionOnlyMismatchIsHard() {
        ProductConfigurationRuleDto rule = ProductConfigurationRuleDto.builder()
                .id(501L)
                .ruleType("VALIDATES")
                .sourceProductId(10L)
                .conditionExpression("attr:color=red")
                .errorMessage("Color must be red")
                .build();

        ConfigurationValidationResult result = ProductRuleEvaluationCore.evaluate(
                List.of(10L),
                id -> List.of(rule),
                Map.of("color", "blue"));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).hasSize(1);
        assertThat(result.getViolations().get(0).getRuleType()).isEqualTo("VALIDATES");
        assertThat(result.getViolations().get(0).getDescription()).isEqualTo("Color must be red");
    }

    @Test
    @DisplayName("expression-only VALIDATES passes when attrs match")
    void expressionOnlyMatchIsValid() {
        ProductConfigurationRuleDto rule = ProductConfigurationRuleDto.builder()
                .id(502L)
                .ruleType("VALIDATES")
                .sourceProductId(10L)
                .conditionExpression("attr:color=red")
                .build();

        ConfigurationValidationResult result = ProductRuleEvaluationCore.evaluate(
                List.of(10L),
                id -> List.of(rule),
                Map.of("color", "red"));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations()).isEmpty();
    }

    @Test
    @DisplayName("expression VALIDATES skipped when attrs empty (no false-fail)")
    void expressionSkippedWhenAttrsEmpty() {
        ProductConfigurationRuleDto rule = ProductConfigurationRuleDto.builder()
                .id(503L)
                .ruleType("VALIDATES")
                .sourceProductId(10L)
                .conditionExpression("attr:color=red")
                .errorMessage("Color must be red")
                .build();

        ConfigurationValidationResult emptyAttrs = ProductRuleEvaluationCore.evaluate(
                List.of(10L),
                id -> List.of(rule),
                Map.of());
        ConfigurationValidationResult nullAttrs = ProductRuleEvaluationCore.evaluate(
                List.of(10L),
                id -> List.of(rule),
                null);
        ConfigurationValidationResult twoArg = ProductRuleEvaluationCore.evaluate(
                List.of(10L),
                id -> List.of(rule));

        assertThat(emptyAttrs.isValid()).isTrue();
        assertThat(nullAttrs.isValid()).isTrue();
        assertThat(twoArg.isValid()).isTrue();
    }

    @Test
    @DisplayName("targetProductId VALIDATES still hard-fails independently of expression")
    void targetProductIdStillHard() {
        ProductConfigurationRuleDto rule = ProductConfigurationRuleDto.builder()
                .id(504L)
                .ruleType("VALIDATES")
                .sourceProductId(10L)
                .targetProductId(40L)
                .conditionExpression("attr:color=red")
                .errorMessage("Needs validated option")
                .build();

        ConfigurationValidationResult result = ProductRuleEvaluationCore.evaluate(
                List.of(10L),
                id -> List.of(rule),
                Map.of("color", "red"));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).hasSize(1);
        assertThat(result.getViolations().get(0).getRuleType()).isEqualTo("VALIDATES");
    }

    @Test
    @DisplayName("targetProductId + expression both checked when attrs present")
    void targetAndExpressionBothFail() {
        ProductConfigurationRuleDto rule = ProductConfigurationRuleDto.builder()
                .id(505L)
                .ruleType("VALIDATES")
                .sourceProductId(10L)
                .targetProductId(40L)
                .conditionExpression("attr:color=red")
                .build();

        ConfigurationValidationResult result = ProductRuleEvaluationCore.evaluate(
                List.of(10L),
                id -> List.of(rule),
                Map.of("color", "blue"));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).hasSize(2);
        assertThat(result.getViolations()).allMatch(v -> "VALIDATES".equals(v.getRuleType()));
    }
}
