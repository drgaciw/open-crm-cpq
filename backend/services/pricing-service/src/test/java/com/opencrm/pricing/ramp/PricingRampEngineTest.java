package com.opencrm.pricing.ramp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.customer.CustomerRegion;
import com.opencrm.pricing.customer.CustomerRepository;
import com.opencrm.pricing.execution.AcvVersion;
import com.opencrm.pricing.execution.AcvVersionProperties;
import com.opencrm.pricing.execution.MetricsCalculator;
import com.opencrm.pricing.execution.PricingAuditLog;
import com.opencrm.pricing.execution.PricingAuditLogRepository;
import com.opencrm.pricing.execution.PricingContextValidator;
import com.opencrm.pricing.execution.PricingExecutionService;
import com.opencrm.pricing.integration.pricebook.PriceBookResolutionClient;
import com.opencrm.pricing.integration.pricebook.ProductEntryResolveDto;
import com.opencrm.pricing.integration.pricebook.ResolveBatchResponse;
import com.opencrm.pricing.product.PricingProductRepository;
import com.opencrm.pricing.product.PricingProduct;
import com.opencrm.pricing.product.ProductType;
import com.opencrm.pricing.rule.RuleRepository;
import com.opencrm.pricing.ruleset.RuleSetRepository;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingLineRequest;
import com.opencrm.pricing.waterfall.PricingResult;
import com.opencrm.pricing.waterfall.WaterfallStepType;
import com.opencrm.common.pricing.RampPriceBasis;
import com.opencrm.common.pricing.RampStep;
import com.opencrm.rules.drools.DroolsRulesEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class PricingRampEngineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private PriceBookResolutionClient priceBookResolutionClient;
    private PricingExecutionService executionService;
    private PricingProductRepository productRepository;

    @BeforeEach
    void setUp() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        priceBookResolutionClient = mock(PriceBookResolutionClient.class);
        productRepository = mock(PricingProductRepository.class);
        PricingAuditLogRepository auditLogRepository = mock(PricingAuditLogRepository.class);
        when(auditLogRepository.save(any(PricingAuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RuleSetRepository ruleSetRepository = mock(RuleSetRepository.class);
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(new ArrayList<>());

        executionService = new PricingExecutionService(
                customerRepository,
                productRepository,
                ruleSetRepository,
                mock(RuleRepository.class),
                priceBookResolutionClient,
                auditLogRepository,
                new PricingContextValidator(),
                new MetricsCalculator(termYears()),
                MAPPER,
                new DroolsRulesEngine(new com.opencrm.rules.drools.DroolsSessionFactory(new com.opencrm.rules.drools.DroolsRuleCompiler()))
        );
        executionService.setRampEnabled(true);
        this.customerRepository = customerRepository;
    }

    private CustomerRepository customerRepository;

    @Test
    @DisplayName("AC-01 prices each absolute period and does not extend year 1 across the term")
    void ac01AbsolutePeriods() throws Exception {
        PricingResult result = execute("ac-01-absolute-three-year.json");
        JsonNode expected = fixture("ac-01-absolute-three-year.json").get("expected");

        assertPeriods(result, expected);
        assertThat(result.getFinancialMetrics().getTcv()).isEqualByComparingTo(expected.get("tcv").decimalValue());
        assertThat(result.getFinancialMetrics().getAcv()).isEqualByComparingTo(expected.get("acv").decimalValue());
        assertThat(result.getFinancialMetrics().getArr()).isEqualByComparingTo(expected.get("arr").decimalValue());
        assertThat(result.getFinancialMetrics().getExitArr()).isEqualByComparingTo(expected.get("exitArr").decimalValue());
        assertThat(result.getFinancialMetrics().getClv()).isEqualByComparingTo(expected.get("clv").decimalValue());
        assertThat(result.getFinancialMetrics().getTcv()).isNotEqualByComparingTo(expected.get("rejectedTcv").get(0).decimalValue());
        assertThat(result.getWaterfall().getSteps()).extracting(step -> step.getStepType())
                .contains(WaterfallStepType.PERIOD_SELECTION);
        verify(priceBookResolutionClient, times(1)).resolveBatch(anyString(), anyList());
    }

    @Test
    @DisplayName("AC-02 applies the 7% named-seat tier to each period's own quantity")
    void ac02PeriodQuantity() throws Exception {
        PricingResult result = execute("ac-02-quantity-named-seat.json");
        JsonNode expected = fixture("ac-02-quantity-named-seat.json").get("expected");

        assertPeriods(result, expected);
        assertThat(result.getFinancialMetrics().getTcv()).isEqualByComparingTo("135600.00");
        assertThat(result.getFinancialMetrics().getAcv()).isEqualByComparingTo("45200.00");
        assertThat(result.getFinancialMetrics().getExitArr()).isEqualByComparingTo("66960.00");
        assertThat(result.getFinancialMetrics().getTcv()).isNotEqualByComparingTo("144000.00");
        assertThat(result.getFinancialMetrics().getTcv()).isNotEqualByComparingTo("133920.00");
        assertThat(result.getLineResults().get(0).getPricedPeriods().get(1).getExplanationCodes())
                .contains("NAMED_SEAT");
        assertThat(result.getLineResults().get(0).getPricedPeriods().get(0).getExplanationCodes())
                .doesNotContain("NAMED_SEAT");
        verify(priceBookResolutionClient, times(1)).resolveBatch(anyString(), anyList());
    }

    @Test
    @DisplayName("AC-08 flag off with a schedule is 422 RAMP_NOT_SUPPORTED")
    void ac08FlagOff() throws Exception {
        executionService.setRampEnabled(false);
        JsonNode root = fixture("ac-08-flag-off.json");
        stub(root);
        PricingContext request = MAPPER.treeToValue(root.get("request"), PricingContext.class);

        assertThatThrownBy(() -> executionService.execute(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> {
                    BusinessException exception = (BusinessException) thrown;
                    assertThat(exception.getErrorCode()).isEqualTo("RAMP_NOT_SUPPORTED");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(exception.getStatus().value()).isEqualTo(422);
                });
    }

    @Test
    @DisplayName("AC-09 replays the same schedule to the same period results")
    void ac09Replay() throws Exception {
        PricingResult first = execute("ac-09-replay.json");
        PricingResult second = execute("ac-09-replay.json");

        assertThat(second.getLineResults().get(0).getPricedPeriods())
                .usingRecursiveComparison()
                .isEqualTo(first.getLineResults().get(0).getPricedPeriods());
        assertThat(second.getFinancialMetrics().getTcv()).isEqualByComparingTo(first.getFinancialMetrics().getTcv());
        assertThat(second.getFinancialMetrics().getExitArr()).isEqualByComparingTo(first.getFinancialMetrics().getExitArr());
        assertThat(first.getFinancialMetrics().getTcv()).isEqualByComparingTo("81000.00");
    }

    @Test
    @DisplayName("named-seat thresholds are 100 → 0.07, 500 → 0.12 and 1000 → 0.18")
    void namedSeatThresholds() {
        assertThat(RampPeriodPricer.namedSeatFraction("SAAS_SEAT_NAMED", 99)).isEqualByComparingTo("0");
        assertThat(RampPeriodPricer.namedSeatFraction("SAAS_SEAT_NAMED", 100)).isEqualByComparingTo("0.07");
        assertThat(RampPeriodPricer.namedSeatFraction("SAAS_SEAT_NAMED", 500)).isEqualByComparingTo("0.12");
        assertThat(RampPeriodPricer.namedSeatFraction("SAAS_SEAT_NAMED", 1000)).isEqualByComparingTo("0.18");
        assertThat(RampPeriodPricer.namedSeatFraction("SUBSCRIPTION", 1000)).isEqualByComparingTo("0");

        List<PricedPeriod> priced = new RampPeriodPricer().price(line(500), new BigDecimal("100.00"), 12, 36);
        assertThat(priced.get(0).getNetUnitPrice()).isEqualByComparingTo("88.0000");
    }

    @Test
    @DisplayName("startup fails when the ramp flag is on and ACV is not term-years")
    void startupRequiresTermYears() {
        RampPricingProperties ramp = new RampPricingProperties();
        ramp.setEnabled(true);
        AcvVersionProperties acv = new AcvVersionProperties();
        assertThatThrownBy(() -> new RampPricingStartup(ramp, acv).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);

        ramp.setEnabled(false);
        new RampPricingStartup(ramp, acv).afterPropertiesSet();

        ramp.setEnabled(true);
        acv.setAcvVersion(AcvVersion.TERM_YEARS);
        new RampPricingStartup(ramp, acv).afterPropertiesSet();
    }

    @Test
    @DisplayName("committed config keeps the ramp flag off, max-periods 36, and legacy ACV")
    void committedConfigStaysOff() throws Exception {
        try (InputStream in = PricingRampEngineTest.class.getResourceAsStream("/application.yml")) {
            assertThat(in).isNotNull();
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(yml).contains("acv-version: legacy");
            assertThat(yml).contains("enabled: false");
            assertThat(yml).contains("max-periods: 36");
            assertThat(yml).doesNotContain("acv-version: term-years");
        }
    }

    @Test
    @DisplayName("FR-01 discount, fee and tax lines reject a schedule before pricing")
    void rejectsScheduleOnNonRecurringLineTypes() {
        for (String type : List.of("DISCOUNT", "FEE", "TAX", " fee ")) {
            PricingLineRequest line = line(10);
            line.setLineItemType(type);
            assertScheduleInvalid(line, "RAMP_NOT_ALLOWED_FOR_LINE_TYPE");
        }
    }

    @Test
    @DisplayName("FR-01 a schedule needs a recurring billing frequency; missing, one-time and perpetual are refused")
    void rejectsScheduleWithoutRecurringBilling() {
        for (String billing : java.util.Arrays.asList(null, "", "ONE_TIME", "PERPETUAL")) {
            PricingLineRequest line = line(10);
            line.setBillingFrequency(billing);
            assertScheduleInvalid(line, "RAMP_NOT_ALLOWED_FOR_LINE_TYPE");
        }
        for (String billing : List.of("MONTHLY", "QUARTERLY", "SEMI_ANNUAL", "ANNUAL", "BIENNIAL", "annual")) {
            PricingLineRequest line = line(10);
            line.setBillingFrequency(billing);
            assertThat(new RampPeriodPricer().price(line, new BigDecimal("100.00"), 12, 36)).hasSize(1);
        }
    }

    @Test
    @DisplayName("FR-02 execute refuses a fee line's schedule with 422 RAMP_SCHEDULE_INVALID")
    void executeRejectsScheduleOnFeeLine() throws Exception {
        JsonNode root = fixture("ac-01-absolute-three-year.json");
        stub(root);
        ObjectNode requestNode = (ObjectNode) root.get("request").deepCopy();
        ((ObjectNode) requestNode.get("lineItems").get(0)).put("lineItemType", "FEE");
        PricingContext request = MAPPER.treeToValue(requestNode, PricingContext.class);

        assertThatThrownBy(() -> executionService.execute(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("RAMP_NOT_ALLOWED_FOR_LINE_TYPE")
                .satisfies(thrown -> {
                    BusinessException exception = (BusinessException) thrown;
                    assertThat(exception.getErrorCode()).isEqualTo("RAMP_SCHEDULE_INVALID");
                    assertThat(exception.getStatus().value()).isEqualTo(422);
                });
    }

    @Test
    @DisplayName("FR-02 a negative price or fraction is RAMP_NEGATIVE_VALUE; a malformed basis is RAMP_PRICE_BASIS")
    void negativeValuesAndMalformedBasisHaveDistinctReasons() {
        assertScheduleInvalid(lineWith(absolute(new BigDecimal("-1.00"))), "RAMP_NEGATIVE_VALUE");
        assertScheduleInvalid(lineWith(fraction(new BigDecimal("-0.10"))), "RAMP_NEGATIVE_VALUE");

        assertScheduleInvalid(lineWith(absolute(null)), "RAMP_PRICE_BASIS");
        RampStep both = absolute(new BigDecimal("40.00"));
        both.setDiscountFraction(new BigDecimal("0.10"));
        assertScheduleInvalid(lineWith(both), "RAMP_PRICE_BASIS");
        assertScheduleInvalid(lineWith(fraction(new BigDecimal("1.50"))), "RAMP_PRICE_BASIS");
        assertScheduleInvalid(lineWith(fraction(null)), "RAMP_PRICE_BASIS");
    }

    private static void assertScheduleInvalid(PricingLineRequest line, String reason) {
        assertThatThrownBy(() -> new RampPeriodPricer().price(line, new BigDecimal("100.00"), 12, 36))
                .isInstanceOf(BusinessException.class)
                .hasMessage(reason)
                .satisfies(thrown -> {
                    BusinessException exception = (BusinessException) thrown;
                    assertThat(exception.getErrorCode()).isEqualTo("RAMP_SCHEDULE_INVALID");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                });
    }

    private static PricingLineRequest lineWith(RampStep step) {
        PricingLineRequest line = line(10);
        line.setSchedule(List.of(step));
        return line;
    }

    private static RampStep absolute(BigDecimal unitPrice) {
        return RampStep.builder()
                .sequence(1)
                .startMonth(1)
                .months(12)
                .quantity(10)
                .priceBasis(RampPriceBasis.ABSOLUTE)
                .unitPrice(unitPrice)
                .build();
    }

    private static RampStep fraction(BigDecimal discountFraction) {
        return RampStep.builder()
                .sequence(1)
                .startMonth(1)
                .months(12)
                .quantity(10)
                .priceBasis(RampPriceBasis.DISCOUNT_FRACTION)
                .discountFraction(discountFraction)
                .build();
    }

    private static AcvVersionProperties termYears() {
        AcvVersionProperties properties = new AcvVersionProperties();
        properties.setAcvVersion(AcvVersion.TERM_YEARS);
        return properties;
    }

    private static PricingLineRequest line(int quantity) {
        return PricingLineRequest.builder()
                .lineItemType("SAAS_SEAT_NAMED")
                .billingFrequency("MONTHLY")
                .quantity(quantity)
                .schedule(List.of(RampStep.builder()
                        .sequence(1)
                        .startMonth(1)
                        .months(12)
                        .quantity(quantity)
                        .priceBasis(RampPriceBasis.DISCOUNT_FRACTION)
                        .discountFraction(BigDecimal.ZERO)
                        .build()))
                .build();
    }

    private PricingResult execute(String name) throws Exception {
        JsonNode root = fixture(name);
        stub(root);
        PricingContext request = MAPPER.treeToValue(root.get("request"), PricingContext.class);
        return executionService.execute(request);
    }

    private void assertPeriods(PricingResult result, JsonNode expected) {
        List<PricedPeriod> periods = result.getLineResults().get(0).getPricedPeriods();
        assertThat(periods).hasSize(expected.get("monthly").size());
        for (int i = 0; i < periods.size(); i++) {
            assertThat(periods.get(i).getNetUnitPrice()).isEqualByComparingTo(expected.get("netUnits").get(i).decimalValue());
            assertThat(periods.get(i).getNetMonthly()).isEqualByComparingTo(expected.get("monthly").get(i).decimalValue());
            assertThat(periods.get(i).getPeriodAmount()).isEqualByComparingTo(expected.get("periodAmounts").get(i).decimalValue());
            assertThat(periods.get(i).getWaterfall()).extracting(step -> step.getStepType())
                    .contains(WaterfallStepType.PERIOD_SELECTION);
        }
    }

    private void stub(JsonNode root) {
        JsonNode customerNode = root.get("stubs").get("customer");
        Customer customer = Customer.builder()
                .externalId(customerNode.get("externalId").asText())
                .name(customerNode.get("name").asText())
                .region(CustomerRegion.valueOf(customerNode.get("region").asText()))
                .churnScore(customerNode.get("churnScore").asInt())
                .build();
        when(customerRepository.findByExternalIdAndDeletedFalse(customer.getExternalId()))
                .thenReturn(Optional.of(customer));

        Map<String, ProductEntryResolveDto> entries = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.get("stubs").get("priceBookEntries").fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            entries.put(field.getKey(), ProductEntryResolveDto.builder()
                    .entryMatched(true)
                    .listPrice(field.getValue().get("listPrice").decimalValue())
                    .currency("USD")
                    .build());
        }
        when(priceBookResolutionClient.resolveBatch(anyString(), anyList()))
                .thenReturn(ResolveBatchResponse.builder().entries(entries).priceBookCurrency("USD").build());
        // AC-01/02/09 sell the synthetic PROD-SEAT named-seat catalog product.
        when(productRepository.findByExternalIdInAndDeletedFalse(anyCollection())).thenReturn(
                entries.containsKey("PROD-SEAT") ? List.of(PricingProduct.builder().externalId("PROD-SEAT")
                        .productType(ProductType.SAAS_SEAT_NAMED).currency("USD")
                        .listPrice(entries.get("PROD-SEAT").getListPrice()).build()) : List.of());
    }

    private static JsonNode fixture(String name) throws Exception {
        try (InputStream in = PricingRampEngineTest.class.getResourceAsStream("/fixtures/pricing/ramp/" + name)) {
            assertThat(in).isNotNull();
            return MAPPER.readTree(in);
        }
    }
}
