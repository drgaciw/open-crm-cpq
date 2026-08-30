package com.opencrm.configuration.evaluate;

import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.configuration.product.ProductConfigurationRuleClient;
import com.opencrm.configuration.product.ProductConfigurationRuleDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Thin REQUIRES/EXCLUDES parity with product-service
 * {@code ConfigurationRuleService.validateConfiguration}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StatelessConfigurationEvaluator")
class StatelessConfigurationEvaluatorTest {

    @Mock
    private ProductConfigurationRuleClient ruleClient;

    @InjectMocks
    private StatelessConfigurationEvaluator evaluator;

    @Test
    @DisplayName("empty selection is valid and does not fetch rules")
    void emptySelectionIsValid() {
        ConfigurationValidationResult result = evaluator.evaluate(Collections.emptyList());

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations()).isEmpty();
        verify(ruleClient, never()).getRulesForProduct(anyLong());
    }

    @Test
    @DisplayName("null selection is valid")
    void nullSelectionIsValid() {
        ConfigurationValidationResult result = evaluator.evaluate(null);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations()).isEmpty();
        verify(ruleClient, never()).getRulesForProduct(anyLong());
    }

    @Test
    @DisplayName("REQUIRES miss yields invalid with REQUIRES violation")
    void requiresMissIsInvalid() {
        when(ruleClient.getRulesForProduct(10L)).thenReturn(List.of(
                ProductConfigurationRuleDto.builder()
                        .id(101L)
                        .ruleType("REQUIRES")
                        .sourceProductId(10L)
                        .targetProductId(20L)
                        .errorMessage("Base requires addon")
                        .build()
        ));

        ConfigurationValidationResult result = evaluator.evaluate(List.of(10L));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).hasSize(1);
        assertThat(result.getViolations().get(0).getRuleId()).isEqualTo(101L);
        assertThat(result.getViolations().get(0).getRuleType()).isEqualTo("REQUIRES");
        assertThat(result.getViolations().get(0).getDescription()).isEqualTo("Base requires addon");
        assertThat(result.getViolations().get(0).getRemediation()).contains("20");
    }

    @Test
    @DisplayName("REQUIRES satisfied when target is selected")
    void requiresSatisfiedIsValid() {
        when(ruleClient.getRulesForProduct(10L)).thenReturn(List.of(
                ProductConfigurationRuleDto.builder()
                        .id(101L)
                        .ruleType("REQUIRES")
                        .sourceProductId(10L)
                        .targetProductId(20L)
                        .build()
        ));
        when(ruleClient.getRulesForProduct(20L)).thenReturn(List.of());

        ConfigurationValidationResult result = evaluator.evaluate(List.of(10L, 20L));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations()).isEmpty();
    }

    @Test
    @DisplayName("EXCLUDES hit yields invalid with EXCLUDES violation")
    void excludesHitIsInvalid() {
        when(ruleClient.getRulesForProduct(10L)).thenReturn(List.of(
                ProductConfigurationRuleDto.builder()
                        .id(202L)
                        .ruleType("EXCLUDES")
                        .sourceProductId(10L)
                        .targetProductId(30L)
                        .errorMessage("Incompatible products")
                        .build()
        ));
        when(ruleClient.getRulesForProduct(30L)).thenReturn(List.of());

        ConfigurationValidationResult result = evaluator.evaluate(List.of(10L, 30L));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).hasSize(1);
        assertThat(result.getViolations().get(0).getRuleId()).isEqualTo(202L);
        assertThat(result.getViolations().get(0).getRuleType()).isEqualTo("EXCLUDES");
        assertThat(result.getViolations().get(0).getDescription()).isEqualTo("Incompatible products");
    }

    @Test
    @DisplayName("RECOMMENDS miss is soft: valid=true with RECOMMENDS violation")
    void recommendsMissIsSoft() {
        when(ruleClient.getRulesForProduct(10L)).thenReturn(List.of(
                ProductConfigurationRuleDto.builder()
                        .id(201L)
                        .ruleType("RECOMMENDS")
                        .sourceProductId(10L)
                        .targetProductId(30L)
                        .errorMessage("Consider addon")
                        .build()
        ));

        ConfigurationValidationResult result = evaluator.evaluate(List.of(10L));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations()).hasSize(1);
        assertThat(result.getViolations().get(0).getRuleType()).isEqualTo("RECOMMENDS");
    }

    @Test
    @DisplayName("DEFAULT expands selection so REQUIRES against default target can pass")
    void defaultExpandsSelection() {
        when(ruleClient.getRulesForProduct(10L)).thenReturn(List.of(
                ProductConfigurationRuleDto.builder()
                        .id(301L)
                        .ruleType("DEFAULT")
                        .sourceProductId(10L)
                        .targetProductId(20L)
                        .build(),
                ProductConfigurationRuleDto.builder()
                        .id(302L)
                        .ruleType("REQUIRES")
                        .sourceProductId(10L)
                        .targetProductId(20L)
                        .build()
        ));
        when(ruleClient.getRulesForProduct(20L)).thenReturn(List.of());

        ConfigurationValidationResult result = evaluator.evaluate(List.of(10L));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations()).isEmpty();
    }

    @Test
    @DisplayName("VALIDATES miss on targetProductId is hard failure")
    void validatesMissIsHard() {
        when(ruleClient.getRulesForProduct(10L)).thenReturn(List.of(
                ProductConfigurationRuleDto.builder()
                        .id(401L)
                        .ruleType("VALIDATES")
                        .sourceProductId(10L)
                        .targetProductId(40L)
                        .conditionExpression("attr:color=red")
                        .errorMessage("Needs validated option")
                        .build()
        ));

        ConfigurationValidationResult result = evaluator.evaluate(List.of(10L));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations().get(0).getRuleType()).isEqualTo("VALIDATES");
    }

    @Test
    @DisplayName("List<Long> agent path skips expression-only VALIDATES (no false-fail)")
    void agentPathSkipsExpressionOnlyValidates() {
        when(ruleClient.getRulesForProduct(10L)).thenReturn(List.of(
                ProductConfigurationRuleDto.builder()
                        .id(402L)
                        .ruleType("VALIDATES")
                        .sourceProductId(10L)
                        .conditionExpression("attr:color=red")
                        .errorMessage("Color must be red")
                        .build()
        ));

        ConfigurationValidationResult result = evaluator.evaluate(List.of(10L));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations()).isEmpty();
    }
}
