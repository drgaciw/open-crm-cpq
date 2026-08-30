package com.opencrm.configuration.constraint;

import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.configuration.product.ProductConfigurationRuleClient;
import com.opencrm.configuration.product.ProductConfigurationRuleDto;
import com.opencrm.configuration.session.ConfigurationSession;
import com.opencrm.configuration.session.ConfigurationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConstraintSolver (Wave G2)")
class ConstraintSolverTest {

    @Mock
    private ProductConfigurationRuleClient ruleClient;

    @InjectMocks
    private ConstraintSolver solver;

    @Test
    @DisplayName("maps numeric productId and selection values to selected product IDs")
    void mapsNumericSelectionsToProductIds() {
        ConfigurationSession session = session("100", Map.of(
                "addon", "200",
                "color", "red",
                "blank", "  "
        ));

        assertThat(ConstraintSolver.selectedProductIds(session)).containsExactly(100L, 200L);
    }

    @Test
    @DisplayName("non-numeric productId alone yields empty selection and no rule fetch")
    void nonNumericProductIdDoesNotFetch() {
        ConfigurationSession session = session("PROD-001", Map.of("color", "red"));

        ConfigurationValidationResult result = solver.evaluate(session);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations()).isEmpty();
        verify(ruleClient, never()).getRulesForProduct(anyLong());
    }

    @Test
    @DisplayName("placeholder color/option_a rules are gone; empty numeric selection is valid")
    void noColorOptionPlaceholder() {
        ConfigurationSession session = session("10", Map.of());
        when(ruleClient.getRulesForProduct(10L)).thenReturn(List.of());

        List<String> errors = solver.validate(session);

        assertThat(errors).isEmpty();
        assertThat(solver.evaluate(session).isValid()).isTrue();
    }

    @Test
    @DisplayName("REQUIRES miss is a hard failure via shared core")
    void requiresMissIsHard() {
        ConfigurationSession session = session("10", Map.of("option", "10"));
        when(ruleClient.getRulesForProduct(10L)).thenReturn(List.of(
                ProductConfigurationRuleDto.builder()
                        .id(1L)
                        .ruleType("REQUIRES")
                        .sourceProductId(10L)
                        .targetProductId(20L)
                        .errorMessage("Base requires addon")
                        .build()
        ));

        ConfigurationValidationResult result = solver.evaluate(session);

        assertThat(result.isValid()).isFalse();
        assertThat(solver.validate(session)).containsExactly("Base requires addon");
    }

    @Test
    @DisplayName("RECOMMENDS miss is soft: evaluate valid=true; validate() omits soft messages")
    void recommendsIsSoft() {
        ConfigurationSession session = session("10", Map.of("base", "10"));
        when(ruleClient.getRulesForProduct(10L)).thenReturn(List.of(
                ProductConfigurationRuleDto.builder()
                        .id(2L)
                        .ruleType("RECOMMENDS")
                        .sourceProductId(10L)
                        .targetProductId(30L)
                        .errorMessage("Consider warranty")
                        .build()
        ));

        ConfigurationValidationResult result = solver.evaluate(session);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations()).hasSize(1);
        assertThat(result.getViolations().get(0).getRuleType()).isEqualTo("RECOMMENDS");
        assertThat(solver.validate(session)).isEmpty();
        assertThat(ConstraintSolver.allViolationMessages(result)).containsExactly("Consider warranty");
    }

    @Test
    @DisplayName("EXCLUDES hit is hard when both products selected")
    void excludesHitIsHard() {
        ConfigurationSession session = session("10", Map.of("a", "10", "b", "30"));
        when(ruleClient.getRulesForProduct(10L)).thenReturn(List.of(
                ProductConfigurationRuleDto.builder()
                        .id(3L)
                        .ruleType("EXCLUDES")
                        .sourceProductId(10L)
                        .targetProductId(30L)
                        .errorMessage("Incompatible pair")
                        .build()
        ));
        when(ruleClient.getRulesForProduct(30L)).thenReturn(List.of());

        assertThat(solver.evaluate(session).isValid()).isFalse();
        assertThat(solver.validate(session)).containsExactly("Incompatible pair");
    }

    @Test
    @DisplayName("isCompatible rejects hard-invalid candidate selection")
    void isCompatibleUsesHardValidity() {
        ConfigurationSession session = session("10", Map.of("base", "10"));
        when(ruleClient.getRulesForProduct(10L)).thenReturn(List.of(
                ProductConfigurationRuleDto.builder()
                        .id(4L)
                        .ruleType("EXCLUDES")
                        .sourceProductId(10L)
                        .targetProductId(99L)
                        .errorMessage("No 99")
                        .build()
        ));
        when(ruleClient.getRulesForProduct(99L)).thenReturn(List.of());

        assertThat(solver.isCompatible(session, "addon", "99")).isFalse();
        assertThat(solver.isCompatible(session, "addon", "blue")).isTrue();
    }

    @Test
    @DisplayName("session selections drive attr:key=value VALIDATES (match → valid)")
    void validatesExpressionMatchViaSelections() {
        ConfigurationSession session = session("10", Map.of("base", "10", "color", "red"));
        when(ruleClient.getRulesForProduct(10L)).thenReturn(List.of(
                ProductConfigurationRuleDto.builder()
                        .id(5L)
                        .ruleType("VALIDATES")
                        .sourceProductId(10L)
                        .conditionExpression("attr:color=red")
                        .build()
        ));

        assertThat(solver.evaluate(session).isValid()).isTrue();
        assertThat(solver.validate(session)).isEmpty();
    }

    @Test
    @DisplayName("session selections drive attr:key=value VALIDATES (mismatch → hard fail)")
    void validatesExpressionMismatchViaSelections() {
        ConfigurationSession session = session("10", Map.of("base", "10", "color", "blue"));
        when(ruleClient.getRulesForProduct(10L)).thenReturn(List.of(
                ProductConfigurationRuleDto.builder()
                        .id(6L)
                        .ruleType("VALIDATES")
                        .sourceProductId(10L)
                        .conditionExpression("attr:color=red")
                        .errorMessage("Color must be red")
                        .build()
        ));

        ConfigurationValidationResult result = solver.evaluate(session);

        assertThat(result.isValid()).isFalse();
        assertThat(solver.validate(session)).containsExactly("Color must be red");
    }

    @Test
    @DisplayName("sessionAttributes copies selection map for expression evaluation")
    void sessionAttributesFromSelections() {
        ConfigurationSession session = session("10", Map.of("color", "red", "size", "L"));
        assertThat(ConstraintSolver.sessionAttributes(session))
                .containsEntry("color", "red")
                .containsEntry("size", "L");
        assertThat(ConstraintSolver.sessionAttributes(session("10", Map.of()))).isEmpty();
    }

    private static ConfigurationSession session(String productId, Map<String, String> selections) {
        return ConfigurationSession.builder()
                .productId(productId)
                .userId("user-1")
                .status(ConfigurationStatus.IN_PROGRESS)
                .selections(new LinkedHashMap<>(selections))
                .valid(false)
                .build();
    }
}
