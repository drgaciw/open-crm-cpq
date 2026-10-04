package com.opencrm.pricing.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.opencrm.pricing.waterfall.PricingWaterfall;
import com.opencrm.pricing.waterfall.PricingWaterfallStep;
import com.opencrm.pricing.waterfall.WaterfallStepType;
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
import static org.mockito.Mockito.when;

/**
 * Flat execute waterfall steps (R1-WF). Header prices and ACV stay on the legacy numbers.
 */
@Tag("unit")
@DisplayName("Pricing waterfall steps")
class PricingWaterfallStepsTest {

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

    static Stream<FlatCase> flatCases() {
        return Stream.of(
                new FlatCase("01-list-price-baseline.json", "100.00", "100.00", "500.00", "6000.00", "6000.00", "500.00"),
                new FlatCase("02-multi-line-override.json", "330.00", "330.00", "740.00", "8880.00", "17760.00", "1480.00"),
                new FlatCase("03-override-only.json", "49.99", "49.99", "499.90", "5998.80", "5998.80", "499.90"),
                new FlatCase("04-evaluate-mapped-happy-path.json", "1200.00", "1200.00", "12000.00", "144000.00", "144000.00", "12000.00")
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
    @MethodSource("flatCases")
    @DisplayName("execute() fills LIST_PRICE, BASE_PRICE and NET_PRICE without moving prices or ACV")
    void executeFillsFlatWaterfallSteps(FlatCase flatCase) throws Exception {
        JsonNode root = readFixture(FIXTURE_DIR + flatCase.fixtureFile());
        PricingContext request = MAPPER.treeToValue(root.get("request"), PricingContext.class);
        stubCustomer(root.get("stubs").get("customer"));
        stubPriceBook(request.getRegion(), root.get("stubs").get("priceBookEntries"), request);
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(new ArrayList<>());
        when(auditLogRepository.save(any(PricingAuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PricingResult result = executionService.execute(request);

        PricingWaterfall waterfall = result.getWaterfall();
        assertThat(waterfall.getListPrice()).isEqualByComparingTo(flatCase.listPrice());
        assertThat(waterfall.getBasePrice()).isEqualByComparingTo(flatCase.basePrice());
        assertThat(waterfall.getNetPrice()).isEqualByComparingTo(flatCase.netPrice());
        assertThat(result.getFinancialMetrics().getArr()).isEqualByComparingTo(flatCase.arr());
        assertThat(result.getFinancialMetrics().getTcv()).isEqualByComparingTo(flatCase.tcv());
        assertThat(result.getFinancialMetrics().getAcv()).isEqualByComparingTo(flatCase.acv());

        List<PricingWaterfallStep> steps = waterfall.getSteps();
        assertThat(steps).extracting(PricingWaterfallStep::getStepType)
                .containsExactly(
                        WaterfallStepType.LIST_PRICE,
                        WaterfallStepType.BASE_PRICE,
                        WaterfallStepType.NET_PRICE);
        assertThat(steps).extracting(PricingWaterfallStep::getDescription)
                .containsExactly(
                        "Sum of line list prices",
                        "Base price",
                        "Sum of line final prices");
        assertStep(steps.get(0), flatCase.listPrice(), flatCase.listPrice());
        assertStep(steps.get(1), flatCase.basePrice(), flatCase.basePrice());
        assertStep(steps.get(2), flatCase.netPrice(), flatCase.netPrice());
        assertThat(steps).allSatisfy(step -> {
            assertThat(step.getRuleId()).isNull();
            assertThat(step.getRuleName()).isNull();
        });
    }

    private static void assertStep(PricingWaterfallStep step, String amount, String runningTotal) {
        assertThat(step.getAmount()).isEqualByComparingTo(amount);
        assertThat(step.getRunningTotal()).isEqualByComparingTo(runningTotal);
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
        when(priceBookResolutionClient.resolveBatch(anyString(), anyList()))
                .thenReturn(response);
    }

    private static BigDecimal decimal(JsonNode node) {
        return node == null || node.isNull() ? null : node.decimalValue();
    }

    private static JsonNode readFixture(String classpath) throws IOException {
        try (InputStream in = PricingWaterfallStepsTest.class.getResourceAsStream(classpath)) {
            assertThat(in).as("fixture %s", classpath).isNotNull();
            return MAPPER.readTree(in);
        }
    }

    record FlatCase(
            String fixtureFile,
            String listPrice,
            String basePrice,
            String netPrice,
            String arr,
            String tcv,
            String acv) {

        @Override
        public String toString() {
            return fixtureFile;
        }
    }
}
