package com.opencrm.configuration.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.dto.ConfigurationViolation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

/**
 * Docker-free F4 contract fixtures mirrored on configuration-engine.
 * <p>
 * Encodes expected {@link ConfigurationValidationResult} hard failures for the same
 * {@code List<Long>} selections used by product-service validate. Live dual-run against
 * {@code StatelessConfigurationEvaluator} is enabled only when that F2 type is on the classpath.
 */
@Tag("unit")
@Tag("contract")
@DisplayName("F4 S2B-11 CE validate parity fixtures (awaits F2 for live dual-run)")
class ConfigurationValidateParityFixtureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FIXTURE = "/contracts/s2b11-validate-parity-fixtures.json";
    private static final String EVALUATOR_CLASS =
            "com.opencrm.configuration.evaluate.StatelessConfigurationEvaluator";

    private static JsonNode root;
    private static JsonNode cases;

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
    @DisplayName("fixture encodes REQUIRES/EXCLUDES hard-failure cases and F2 wait note")
    void fixtureEncodesRequiresExcludesAndF2Wait() {
        assertTrue(root.path("waitingOn").asText().contains("F2"),
                "Must document waiting on F2 for live dual-run");
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
    @DisplayName("live dual-run deferred until F2 evaluator lands on classpath")
    void liveDualRunDeferredUntilF2() {
        if (f2EvaluatorPresent()) {
            fail("F2 " + EVALUATOR_CLASS + " is present — wire live dual-run against "
                    + FIXTURE + " (same List<Long> cases as product validate)");
        }
        assertTrue(root.path("waitingOn").asText().contains("F2"));
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
}
