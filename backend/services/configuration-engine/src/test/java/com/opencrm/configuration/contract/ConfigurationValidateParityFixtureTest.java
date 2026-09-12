package com.opencrm.configuration.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.dto.ConfigurationViolation;
import com.opencrm.configuration.evaluate.StatelessConfigurationEvaluator;
import com.opencrm.configuration.product.ProductConfigurationRuleClient;
import com.opencrm.configuration.product.ProductConfigurationRuleDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Docker-free F4 contract fixtures mirrored on configuration-engine.
 * <p>
 * Encodes expected {@link ConfigurationValidationResult} hard failures for the same
 * {@code List<Long>} selections used by product-service validate, and live dual-runs
 * {@link StatelessConfigurationEvaluator} against those fixtures.
 */
@Tag("unit")
@Tag("contract")
@ExtendWith(MockitoExtension.class)
@DisplayName("F4 S2B-11 CE validate parity fixtures + live dual-run")
class ConfigurationValidateParityFixtureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FIXTURE = "/contracts/s2b11-validate-parity-fixtures.json";
    private static final String EVALUATOR_CLASS =
            "com.opencrm.configuration.evaluate.StatelessConfigurationEvaluator";

    private static JsonNode root;
    private static JsonNode cases;

    @Mock
    private ProductConfigurationRuleClient ruleClient;

    @InjectMocks
    private StatelessConfigurationEvaluator evaluator;

    @BeforeAll
    static void loadFixtures() throws Exception {
        try (InputStream in = ConfigurationValidateParityFixtureTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(in, "Missing classpath fixture " + FIXTURE);
            root = MAPPER.readTree(in);
            cases = root.get("cases");
            assertNotNull(cases, "Fixture must declare cases[]");
            assertTrue(cases.isArray() && cases.size() > 0, "Fixture must list at least one case");
        }
    }

    @BeforeEach
    void resetStubs() {
        // Parameterized cases stub per-selection; lenient empty default for unused products.
        lenient().when(ruleClient.getRulesForProduct(anyLong())).thenReturn(List.of());
    }

    static Stream<Arguments> parityCases() {
        return StreamSupport.stream(cases.spliterator(), false)
                .map(c -> Arguments.of(c.get("id").asText(), c));
    }

    static boolean f2EvaluatorPresent() {
        try {
            Class.forName(EVALUATOR_CLASS);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    @Test
    @DisplayName("fixture encodes REQUIRES/EXCLUDES hard-failure cases and live dual-run note")
    void fixtureEncodesRequiresExcludesAndLiveDualRun() {
        assertTrue(root.path("liveDualRun").asText("").contains("StatelessConfigurationEvaluator")
                        || root.path("waitingOn").asText("").contains("F2"),
                "Must document live dual-run (or legacy F2 wait) against evaluator");
        assertTrue(root.path("equivalence").asText().contains("REQUIRES/EXCLUDES"));

        boolean sawRequires = false;
        boolean sawExcludes = false;
        for (JsonNode c : cases) {
            for (JsonNode rule : c.path("rules")) {
                String type = rule.path("ruleType").asText();
                if ("REQUIRES".equals(type)) {
                    sawRequires = true;
                }
                if ("EXCLUDES".equals(type)) {
                    sawExcludes = true;
                }
            }
        }
        assertTrue(sawRequires, "Fixture must include REQUIRES cases");
        assertTrue(sawExcludes, "Fixture must include EXCLUDES cases");
    }

    @Test
    @DisplayName("F2 evaluator must remain on classpath for live dual-run")
    void f2EvaluatorRequiredForLiveDualRun() {
        if (!f2EvaluatorPresent()) {
            fail("F2 " + EVALUATOR_CLASS + " missing — restore evaluator before dual-run against "
                    + FIXTURE);
        }
        assertTrue(f2EvaluatorPresent());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parityCases")
    @DisplayName("live dual-run: StatelessConfigurationEvaluator matches fixture hard failures")
    void liveDualRunMatchesFixtureHardFailures(String caseId, JsonNode fixtureCase) {
        if (!f2EvaluatorPresent()) {
            fail("F2 " + EVALUATOR_CLASS + " is absent — cannot dual-run " + FIXTURE);
        }

        List<Long> selected = toLongList(fixtureCase.get("selectedProductIds"));
        List<ProductConfigurationRuleDto> rules = toRuleDtos(fixtureCase.get("rules"));
        JsonNode expectedCe = fixtureCase.get("expectedCe");
        assertNotNull(expectedCe, caseId);
        List<String> productErrors = toStringList(fixtureCase.get("expectedProductErrors"));

        if (selected.isEmpty()) {
            ConfigurationValidationResult result = evaluator.evaluate(selected);
            assertTrue(result.isValid(), caseId);
            assertTrue(hardViolations(result).isEmpty(), caseId);
            verify(ruleClient, never()).getRulesForProduct(anyLong());
            return;
        }

        when(ruleClient.getRulesForProduct(anyLong())).thenAnswer(invocation -> {
            Long productId = invocation.getArgument(0);
            return rules.stream()
                    .filter(r -> productId.equals(r.getSourceProductId()))
                    .toList();
        });

        ConfigurationValidationResult result = evaluator.evaluate(selected);

        boolean expectedValid = expectedCe.get("valid").asBoolean();
        assertEquals(expectedValid, result.isValid(),
                () -> caseId + ": evaluator.valid must match fixture expectedCe.valid");

        List<ConfigurationViolation> hard = hardViolations(result);
        List<ConfigurationViolation> expectedHard = toViolations(expectedCe.get("violations")).stream()
                .filter(ConfigurationValidateParityFixtureTest::isHardFailureType)
                .toList();

        assertEquals(expectedHard.size(), hard.size(),
                () -> caseId + ": hard violation count must match fixture (REQUIRES/EXCLUDES)");
        assertEquals(productErrors.size(), hard.size(),
                () -> caseId + ": hard violations must equal product hard-failure count");

        for (int i = 0; i < expectedHard.size(); i++) {
            ConfigurationViolation actual = hard.get(i);
            ConfigurationViolation expected = expectedHard.get(i);
            assertEquals(expected.getRuleType(), actual.getRuleType(), caseId);
            assertEquals(expected.getDescription(), actual.getDescription(), caseId);
            assertEquals(productErrors.get(i), actual.getDescription(), caseId);
            if (expected.getRuleId() != null) {
                assertEquals(expected.getRuleId(), actual.getRuleId(), caseId);
            }
        }

        if (expectedValid) {
            assertTrue(hard.isEmpty(), caseId);
        } else {
            assertFalse(result.isValid(), caseId);
            assertFalse(hard.isEmpty(), caseId);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parityCases")
    @DisplayName("documented CE expected result matches product hard-failure equivalence")
    void documentedCeExpectedMatchesProductEquivalence(String caseId, JsonNode fixtureCase) {
        List<String> productErrors = toStringList(fixtureCase.get("expectedProductErrors"));
        JsonNode expectedCe = fixtureCase.get("expectedCe");
        assertNotNull(expectedCe, caseId);

        boolean productValid = productErrors.isEmpty();
        assertEquals(productValid, expectedCe.get("valid").asBoolean(), caseId);

        List<ConfigurationViolation> violations = toViolations(expectedCe.get("violations"));
        ConfigurationValidationResult result = ConfigurationValidationResult.invalid(violations);
        // invalid() sets valid=true when violations empty — same as ok()
        assertEquals(productValid, result.isValid(), caseId);
        assertEquals(productErrors.size(), result.getViolations().size(), caseId);

        for (int i = 0; i < productErrors.size(); i++) {
            assertEquals(productErrors.get(i), result.getViolations().get(i).getDescription(), caseId);
            String ruleType = result.getViolations().get(i).getRuleType();
            assertTrue("REQUIRES".equals(ruleType) || "EXCLUDES".equals(ruleType), caseId);
        }

        if (productValid) {
            assertTrue(result.getViolations().isEmpty(), caseId);
        } else {
            assertFalse(result.isValid(), caseId);
        }
    }

    private static List<ConfigurationViolation> hardViolations(ConfigurationValidationResult result) {
        if (result.getViolations() == null) {
            return List.of();
        }
        return result.getViolations().stream()
                .filter(ConfigurationValidateParityFixtureTest::isHardFailureType)
                .toList();
    }

    private static boolean isHardFailureType(ConfigurationViolation violation) {
        if (violation == null || violation.getRuleType() == null) {
            return false;
        }
        String type = violation.getRuleType().trim().toUpperCase();
        return "REQUIRES".equals(type) || "EXCLUDES".equals(type) || "VALIDATES".equals(type);
    }

    private static List<Long> toLongList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<Long> values = new ArrayList<>();
        node.forEach(n -> values.add(n.asLong()));
        return values;
    }

    private static List<String> toStringList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(n -> values.add(n.asText()));
        return values;
    }

    private static List<ConfigurationViolation> toViolations(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<ConfigurationViolation> violations = new ArrayList<>();
        node.forEach(v -> violations.add(ConfigurationViolation.builder()
                .ruleId(v.hasNonNull("ruleId") ? v.get("ruleId").asLong() : null)
                .ruleType(v.path("ruleType").asText(null))
                .description(v.path("description").asText(null))
                .remediation(v.path("remediation").asText(null))
                .build()));
        return violations;
    }

    private static List<ProductConfigurationRuleDto> toRuleDtos(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<ProductConfigurationRuleDto> rules = new ArrayList<>();
        node.forEach(r -> rules.add(ProductConfigurationRuleDto.builder()
                .id(r.hasNonNull("id") ? r.get("id").asLong() : null)
                .name(r.path("name").asText(null))
                .ruleType(r.path("ruleType").asText(null))
                .sourceProductId(r.hasNonNull("sourceProductId") ? r.get("sourceProductId").asLong() : null)
                .targetProductId(r.hasNonNull("targetProductId") ? r.get("targetProductId").asLong() : null)
                .errorMessage(r.hasNonNull("errorMessage") ? r.get("errorMessage").asText() : null)
                .priority(r.path("priority").asInt(100))
                .build()));
        return rules;
    }
}
