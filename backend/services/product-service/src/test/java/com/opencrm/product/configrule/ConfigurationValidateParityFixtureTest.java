package com.opencrm.product.configrule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.dto.ConfigurationViolation;
import com.opencrm.product.catalog.ProductRepository;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Docker-free F4 parity fixtures: product {@code validateConfiguration} hard failures
 * must stay equivalent to the documented CE evaluate {@link ConfigurationValidationResult}
 * for the same {@code List<Long>} selections (REQUIRES / EXCLUDES only).
 * <p>
 * Live dual-run against {@code StatelessConfigurationEvaluator} waits on F2 merge;
 * this suite locks the shared oracle and exercises the product side today.
 */
@Tag("unit")
@Tag("contract")
@ExtendWith(MockitoExtension.class)
@DisplayName("F4 S2B-11 product↔CE validate parity fixtures")
class ConfigurationValidateParityFixtureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FIXTURE = "/contracts/s2b11-validate-parity-fixtures.json";

    private static JsonNode cases;

    @Mock
    private ConfigurationRuleRepository configRuleRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ConfigurationRuleMapper configRuleMapper;

    @InjectMocks
    private ConfigurationRuleService configRuleService;

    @BeforeAll
    static void loadFixtures() throws Exception {
        try (InputStream in = ConfigurationValidateParityFixtureTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(in, "Missing classpath fixture " + FIXTURE);
            JsonNode root = MAPPER.readTree(in);
            cases = root.get("cases");
            assertNotNull(cases, "Fixture must declare cases[]");
            assertTrue(cases.isArray() && cases.size() > 0, "Fixture must list at least one case");
        }
    }

    static Stream<Arguments> parityCases() {
        return StreamSupport.stream(cases.spliterator(), false)
                .map(c -> Arguments.of(c.get("id").asText(), c));
    }

    @Test
    @DisplayName("fixture documents product↔CE hard-failure equivalence contract")
    void fixtureDocumentsEquivalenceContract() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(FIXTURE)) {
            JsonNode root = MAPPER.readTree(in);
            assertTrue(root.path("equivalence").asText().contains("REQUIRES/EXCLUDES"),
                    "Fixture must document REQUIRES/EXCLUDES equivalence");
            assertTrue(root.path("waitingOn").asText().contains("F2"),
                    "Fixture must note waiting on F2 for live dual-run");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parityCases")
    @DisplayName("product validate hard failures match fixture oracle")
    void productValidateMatchesFixture(String caseId, JsonNode fixtureCase) {
        List<Long> selected = toLongList(fixtureCase.get("selectedProductIds"));
        List<ConfigurationRule> rules = toRules(fixtureCase.get("rules"));
        List<String> expectedErrors = toStringList(fixtureCase.get("expectedProductErrors"));

        // Empty selection returns early without hitting the repository (strict stubs).
        if (!selected.isEmpty()) {
            when(configRuleRepository.findBySourceProductIdInAndDeletedFalse(anyList()))
                    .thenAnswer(invocation -> {
                        List<Long> ids = invocation.getArgument(0);
                        return rules.stream()
                                .filter(r -> ids.contains(r.getSourceProductId()))
                                .toList();
                    });
        } else {
            lenient().when(configRuleRepository.findBySourceProductIdInAndDeletedFalse(anyList()))
                    .thenReturn(List.of());
        }

        List<String> actual = configRuleService.validateConfiguration(selected);

        assertEquals(expectedErrors, actual,
                () -> caseId + ": product validate errors drifted from F4 fixture oracle");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parityCases")
    @DisplayName("fixture CE expected result is equivalent to product hard failures")
    void fixtureCeExpectedEquivalentToProductHardFailures(String caseId, JsonNode fixtureCase) {
        List<String> productErrors = toStringList(fixtureCase.get("expectedProductErrors"));
        JsonNode expectedCe = fixtureCase.get("expectedCe");
        assertNotNull(expectedCe, caseId + ": expectedCe required");

        boolean productValid = productErrors.isEmpty();
        assertEquals(productValid, expectedCe.get("valid").asBoolean(),
                () -> caseId + ": CE.valid must mirror empty product errors");

        List<ConfigurationViolation> violations = toViolations(expectedCe.get("violations"));
        ConfigurationValidationResult ceResult = productValid
                ? ConfigurationValidationResult.ok()
                : ConfigurationValidationResult.invalid(violations);

        assertEquals(productValid, ceResult.isValid(), caseId);
        assertEquals(productErrors.size(), ceResult.getViolations().size(),
                () -> caseId + ": CE violation count must equal product hard-failure count");

        for (int i = 0; i < productErrors.size(); i++) {
            ConfigurationViolation violation = ceResult.getViolations().get(i);
            assertEquals(productErrors.get(i), violation.getDescription(),
                    () -> caseId + ": CE violation.description must match product error string");
            assertTrue(
                    "REQUIRES".equals(violation.getRuleType()) || "EXCLUDES".equals(violation.getRuleType()),
                    () -> caseId + ": Wave F hard failures are REQUIRES/EXCLUDES only");
        }

        if (!productValid) {
            assertFalse(ceResult.getViolations().isEmpty(), caseId);
        }
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

    private static List<ConfigurationRule> toRules(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<ConfigurationRule> rules = new ArrayList<>();
        node.forEach(r -> {
            ConfigurationRule rule = ConfigurationRule.builder()
                    .name(r.path("name").asText("rule"))
                    .ruleType(ConfigRuleType.valueOf(r.get("ruleType").asText()))
                    .sourceProductId(r.get("sourceProductId").asLong())
                    .targetProductId(r.hasNonNull("targetProductId") ? r.get("targetProductId").asLong() : null)
                    .errorMessage(r.hasNonNull("errorMessage") ? r.get("errorMessage").asText() : null)
                    .priority(r.path("priority").asInt(100))
                    .build();
            if (r.hasNonNull("id")) {
                rule.setId(r.get("id").asLong());
            }
            rules.add(rule);
        });
        return rules;
    }
}
