package com.opencrm.pricing.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.customer.CustomerRegion;
import com.opencrm.pricing.customer.CustomerRepository;
import com.opencrm.pricing.integration.pricebook.PriceBookResolutionClient;
import com.opencrm.pricing.integration.pricebook.ProductEntryResolveDto;
import com.opencrm.pricing.integration.pricebook.ResolveBatchResponse;
import com.opencrm.pricing.product.PricingProductRepository;
import com.opencrm.pricing.rule.RuleRepository;
import com.opencrm.pricing.ruleset.RuleSetRepository;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingResult;
import com.opencrm.rules.drools.DroolsRulesEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S2C-1b golden fixtures for {@code POST /api/v1/pricing/execute} orchestration.
 * Docker-free: stubbed price book + repositories; real validator/metrics.
 * Matrix: {@code docs/backlog/CPQ_API_CONTRACT_MATRIX.md} §4.
 */
@Tag("unit")
@DisplayName("Pricing execute golden fixtures (S2C-1b)")
class PricingExecuteGoldenFixtureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String FIXTURE_DIR = "/fixtures/pricing/execute/";

    private CustomerRepository customerRepository;
    private PricingProductRepository pricingProductRepository;
    private RuleSetRepository ruleSetRepository;
    private RuleRepository ruleRepository;
    private PriceBookResolutionClient priceBookResolutionClient;
    private PricingAuditLogRepository auditLogRepository;
    private DroolsRulesEngine droolsRulesEngine;
    private PricingExecutionService executionService;

    static Stream<String> executeFixtures() {
        return Stream.of(
                "01-list-price-baseline.json",
                "02-multi-line-override.json",
                "03-override-only.json"
        );
    }

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        pricingProductRepository = mock(PricingProductRepository.class);
        ruleSetRepository = mock(RuleSetRepository.class);
        ruleRepository = mock(RuleRepository.class);
        priceBookResolutionClient = mock(PriceBookResolutionClient.class);
        auditLogRepository = mock(PricingAuditLogRepository.class);
        droolsRulesEngine = mock(DroolsRulesEngine.class);

        executionService = new PricingExecutionService(
                customerRepository,
                pricingProductRepository,
                ruleSetRepository,
                ruleRepository,
                priceBookResolutionClient,
                auditLogRepository,
                new PricingContextValidator(),
                new MetricsCalculator(),
                MAPPER,
                droolsRulesEngine
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("executeFixtures")
    @DisplayName("execute() matches golden fixture")
    void executeMatchesGolden(String fixtureFile) throws Exception {
        JsonNode root = readFixture(FIXTURE_DIR + fixtureFile);
        PricingContext request = MAPPER.treeToValue(root.get("request"), PricingContext.class);
        JsonNode stubs = root.get("stubs");
        JsonNode expected = root.get("expected");

        stubCustomer(stubs.get("customer"));
        stubPriceBook(request.getRegion(), stubs.get("priceBookEntries"), request);
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(new ArrayList<>());
        when(auditLogRepository.save(any(PricingAuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PricingResult result = executionService.execute(request);

        assertThat(result.getExecutionTimeMs()).isNotNull();
        assertGolden(expected, result);
        verify(auditLogRepository).save(any(PricingAuditLog.class));
    }

    private void stubCustomer(JsonNode customerNode) {
        Customer customer = Customer.builder()
                .externalId(customerNode.get("externalId").asText())
                .name(customerNode.get("name").asText())
                .region(CustomerRegion.valueOf(customerNode.get("region").asText()))
                .churnScore(customerNode.get("churnScore").asInt())
                .build();
        when(customerRepository.findByExternalIdAndDeletedFalse(customer.getExternalId()))
                .thenReturn(Optional.of(customer));
    }

    private void stubPriceBook(String region, JsonNode entriesNode, PricingContext request) {
        Map<String, ProductEntryResolveDto> entries = new HashMap<>();
        if (entriesNode != null && entriesNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = entriesNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                entries.put(field.getKey(), ProductEntryResolveDto.builder()
                        .entryMatched(value.path("entryMatched").asBoolean(true))
                        .listPrice(decimal(value.get("listPrice")))
                        .currency(value.path("currency").asText("USD"))
                        .build());
            }
        }

        List<String> expectedProducts = request.getLineItems().stream()
                .filter(line -> line.getListPriceOverride() == null)
                .map(line -> line.getProductExternalId())
                .distinct()
                .toList();

        ResolveBatchResponse response = ResolveBatchResponse.builder()
                .priceBookId(1L)
                .priceBookCurrency("USD")
                .entries(entries)
                .build();

        when(priceBookResolutionClient.resolveBatch(
                ArgumentMatchers.eq(region),
                ArgumentMatchers.eq(expectedProducts)))
                .thenReturn(response);

        // Fallback if ordering differs
        when(priceBookResolutionClient.resolveBatch(anyString(), anyList()))
                .thenReturn(response);
    }

    private void assertGolden(JsonNode expected, PricingResult result) throws IOException {
        ObjectNode actual = (ObjectNode) MAPPER.valueToTree(result);
        actual.remove("executionTimeMs");
        actual.remove("errorMessage");
        actual.remove("approvalTier");
        stripLineNoise(actual);

        ObjectNode expectedNode = expected.deepCopy();
        normalizeNumbers(expectedNode);
        normalizeNumbers(actual);

        assertThat(actual.get("success")).isEqualTo(expectedNode.get("success"));
        assertThat(actual.get("approvalState")).isEqualTo(expectedNode.get("approvalState"));
        assertThat(actual.get("lineResults")).isEqualTo(expectedNode.get("lineResults"));
        assertThat(actual.get("waterfall")).isEqualTo(expectedNode.get("waterfall"));
        assertThat(actual.get("financialMetrics")).isEqualTo(expectedNode.get("financialMetrics"));
    }

    private void stripLineNoise(ObjectNode actual) {
        JsonNode lines = actual.get("lineResults");
        if (lines != null && lines.isArray()) {
            for (JsonNode line : lines) {
                if (line instanceof ObjectNode objectNode) {
                    objectNode.remove("explanationCodes");
                    objectNode.remove("metadata");
                }
            }
        }
        JsonNode waterfall = actual.get("waterfall");
        if (waterfall instanceof ObjectNode waterfallNode) {
            waterfallNode.remove("explanationCodes");
        }
        JsonNode explanationCodes = actual.get("explanationCodes");
        if (explanationCodes != null && explanationCodes.isArray() && explanationCodes.isEmpty()) {
            actual.remove("explanationCodes");
        }
    }

    private void normalizeNumbers(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
            fields.forEachRemaining(entries::add);
            for (Map.Entry<String, JsonNode> entry : entries) {
                JsonNode value = entry.getValue();
                if (value.isNumber()) {
                    objectNode.set(entry.getKey(), MAPPER.getNodeFactory().numberNode(value.decimalValue()));
                } else {
                    normalizeNumbers(value);
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode value = arrayNode.get(i);
                if (value.isNumber()) {
                    arrayNode.set(i, MAPPER.getNodeFactory().numberNode(value.decimalValue()));
                } else {
                    normalizeNumbers(value);
                }
            }
        }
    }

    private static BigDecimal decimal(JsonNode node) {
        return node == null || node.isNull() ? null : node.decimalValue();
    }

    private static JsonNode readFixture(String classpath) throws IOException {
        try (InputStream in = PricingExecuteGoldenFixtureTest.class.getResourceAsStream(classpath)) {
            assertThat(in).as("fixture %s", classpath).isNotNull();
            return MAPPER.readTree(in);
        }
    }
}
