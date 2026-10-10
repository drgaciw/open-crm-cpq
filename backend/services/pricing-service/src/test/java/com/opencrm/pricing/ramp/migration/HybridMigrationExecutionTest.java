package com.opencrm.pricing.ramp.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.customer.*;
import com.opencrm.pricing.execution.*;
import com.opencrm.pricing.integration.pricebook.*;
import com.opencrm.pricing.product.*;
import com.opencrm.pricing.rule.RuleRepository;
import com.opencrm.pricing.ruleset.RuleSetRepository;
import com.opencrm.pricing.waterfall.*;
import com.opencrm.rules.drools.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class HybridMigrationExecutionTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private PricingExecutionService service;
    private PricingProductRepository products;
    private PriceBookResolutionClient pricebook;
    private PricingAuditLogRepository audit;
    private PricingContext context;
    private List<PricingProduct> authoritative;

    @BeforeEach
    void setUp() throws Exception {
        context = MAPPER.readValue(getClass().getResourceAsStream("/fixtures/pricing/ramp/migration/request.json"), PricingContext.class);
        authoritative = new ArrayList<>(Arrays.asList(MAPPER.readValue(getClass().getResourceAsStream("/fixtures/pricing/ramp/migration/authoritative-products.json"), PricingProduct[].class)));
        var customers = mock(CustomerRepository.class);
        when(customers.findByExternalIdAndDeletedFalse(anyString())).thenReturn(Optional.of(Customer.builder()
                .externalId(context.getCustomerExternalId()).region(CustomerRegion.NA).name("Synthetic customer").build()));
        products = mock(PricingProductRepository.class);
        when(products.findByExternalIdInAndDeletedFalse(anyCollection())).thenAnswer(call -> authoritative);
        pricebook = mock(PriceBookResolutionClient.class);
        Map<String, ProductEntryResolveDto> entries = new HashMap<>();
        for (var product : authoritative) entries.put(product.getExternalId(), ProductEntryResolveDto.builder()
                .entryMatched(true).listPrice(product.getListPrice()).currency(product.getCurrency()).build());
        when(pricebook.resolveBatch(anyString(), anyList())).thenReturn(ResolveBatchResponse.builder()
                .entries(entries).priceBookCurrency("USD").build());
        var rulesets = mock(RuleSetRepository.class);
        when(rulesets.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any())).thenReturn(List.of());
        audit = mock(PricingAuditLogRepository.class);
        when(audit.save(any())).thenAnswer(call -> call.getArgument(0));
        var acv = new AcvVersionProperties();
        acv.setAcvVersion(AcvVersion.TERM_YEARS);
        service = new PricingExecutionService(customers, products, rulesets, mock(RuleRepository.class), pricebook,
                audit, new PricingContextValidator(), new MetricsCalculator(acv), MAPPER,
                new DroolsRulesEngine(new DroolsSessionFactory(new DroolsRuleCompiler())));
        service.setRampEnabled(true);
    }

    @Test
    void goldenCreditIsAppliedOnceWithoutChangingRecurringRatesAndAudit() throws Exception {
        var result = service.execute(context);
        var decline = result.getLineResults().getFirst().getPricedPeriods();
        assertThat(decline.getFirst().getPeriodAmount()).isEqualByComparingTo("9000");
        var json = MAPPER.valueToTree(result);
        var first = json.path("lineResults").get(0).path("pricedPeriods").get(0);
        assertThat(first.path("grossPeriodAmount").decimalValue()).isEqualByComparingTo("12000");
        assertThat(first.path("migrationCredit").decimalValue()).isEqualByComparingTo("3000");
        assertThat(decline.getFirst().getNetUnitPrice()).isEqualByComparingTo("100");
        assertThat(decline.getFirst().getNetMonthly()).isEqualByComparingTo("1000");
        assertThat(decline.getLast().getPeriodAmount()).isEqualByComparingTo("0");
        for (int line = 0; line < 2; line++) for (int period = 0; period < 2; period++) {
            if (line == 0 && period == 0) continue;
            var ordinary = json.path("lineResults").get(line).path("pricedPeriods").get(period);
            assertThat(ordinary.has("grossPeriodAmount")).isFalse();
            assertThat(ordinary.has("migrationCredit")).isFalse();
        }
        assertThat(result.getFinancialMetrics().getTcv()).isEqualByComparingTo("43800");
        assertThat(result.getFinancialMetrics().getAcv()).isEqualByComparingTo("14600");
        assertThat(result.getFinancialMetrics().getArr()).isEqualByComparingTo("18000");
        assertThat(result.getFinancialMetrics().getExitArr()).isEqualByComparingTo("14400");
        assertThat(result.getFinancialMetrics().getClv()).isEqualByComparingTo("87600");
        assertThat(result.getApprovalState()).isEqualTo("PENDING");
        assertThat(result.getApprovalTier()).isEqualTo("DEAL_DESK");
        assertThat(result.getExplanationCodes()).contains("RAMP_HYBRID_WINDOW").doesNotContain("RAMP_STEP_DOWN");
        assertThat(result.getLineResults().get(1).getPricedPeriods().getLast().getNetUnitPrice()).isEqualByComparingTo("60");
        assertThat(result.getWaterfall().getSteps().stream()
                .filter(step -> step.getStepType() == WaterfallStepType.PERIOD_SELECTION || "hybrid-migration-credit".equals(step.getRuleId()))
                .map(PricingWaterfallStep::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("43800");
        verify(products, times(1)).findByExternalIdInAndDeletedFalse(anyCollection());
        verify(pricebook, times(1)).resolveBatch(anyString(), anyList());
        verify(audit).save(argThat(log -> log.getOutputSnapshot().contains("\"migrationCredit\":3000")
                && log.getInputSnapshot().contains("\"bookValue\":10000")));
        String capturePath = System.getProperty("r4.acceptance.output");
        if (capturePath != null) {
            java.nio.file.Files.writeString(java.nio.file.Path.of(capturePath),
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }

    @Test
    void risingQuantityCannotDecreaseWithinAuthorizedOverlap() {
        var original = context.getLineItems().get(1).getSchedule();
        var first = original.getFirst();
        context.getLineItems().get(1).setSchedule(List.of(
                com.opencrm.common.pricing.RampStep.builder().sequence(1).startMonth(1).months(6).quantity(10)
                        .priceBasis(first.getPriceBasis()).unitPrice(first.getUnitPrice()).build(),
                com.opencrm.common.pricing.RampStep.builder().sequence(2).startMonth(7).months(6).quantity(8)
                        .priceBasis(first.getPriceBasis()).unitPrice(first.getUnitPrice()).build(),
                com.opencrm.common.pricing.RampStep.builder().sequence(3).startMonth(13).months(24).quantity(20)
                        .priceBasis(original.getLast().getPriceBasis()).unitPrice(original.getLast().getUnitPrice()).build()));
        assertThatThrownBy(() -> service.execute(context)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("direction");
    }

    @Test
    void decliningQuantityCannotIncreaseWithinAuthorizedOverlap() {
        var original = context.getLineItems().getFirst().getSchedule();
        var first = original.getFirst();
        context.getLineItems().getFirst().setSchedule(List.of(
                com.opencrm.common.pricing.RampStep.builder().sequence(1).startMonth(1).months(6).quantity(10)
                        .priceBasis(first.getPriceBasis()).unitPrice(first.getUnitPrice()).build(),
                com.opencrm.common.pricing.RampStep.builder().sequence(2).startMonth(7).months(6).quantity(12)
                        .priceBasis(first.getPriceBasis()).unitPrice(first.getUnitPrice()).build(),
                com.opencrm.common.pricing.RampStep.builder().sequence(3).startMonth(13).months(24).quantity(0)
                        .priceBasis(original.getLast().getPriceBasis()).unitPrice(original.getLast().getUnitPrice()).build()));
        assertThatThrownBy(() -> service.execute(context)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("direction");
    }

    @Test
    void noSpillAndZeroBookStillRequireHybridApproval() {
        migration(0).put("bookValue", new BigDecimal("1000000"));
        var capped = service.simulate(context);
        assertThat(capped.getLineResults().getFirst().getPricedPeriods().getFirst().getPeriodAmount()).isEqualByComparingTo("0");
        assertThat(capped.getLineResults().getFirst().getPricedPeriods().getLast().getPeriodAmount()).isEqualByComparingTo("0");
        assertThat(capped.getLineResults().get(1).getPricedPeriods().getFirst().getPeriodAmount()).isEqualByComparingTo("6000");
        migration(0).put("bookValue", BigDecimal.ZERO);
        var zero = service.simulate(context);
        assertThat(zero.getApprovalState()).isEqualTo("PENDING");
        assertThat(zero.getExplanationCodes()).contains("RAMP_HYBRID_WINDOW");
    }

    @Test
    void thirteenMonthActualOverlapFailsBeforeAnyAudit() {
        for (var line : context.getLineItems()) {
            migration(context.getLineItems().indexOf(line)).put("months", 13);
            line.getSchedule().getFirst().setMonths(13);
            line.getSchedule().getLast().setStartMonth(14);
            line.getSchedule().getLast().setMonths(23);
        }
        invalid();
        verify(audit, never()).save(any());
    }

    @Test
    void declaredWindowCannotHideEntitlementAndFreePriceStillCounts() {
        var late = context.getLineItems().getFirst().getSchedule().getLast();
        late.setQuantity(1);
        late.setUnitPrice(BigDecimal.ZERO);
        invalid();
    }

    @Test
    void authoritativeTypeCurrencyAndPartnerAreRequired() {
        authoritative.get(1).setProductType(ProductType.SAAS_SEAT_NAMED);
        invalid();
        authoritative.get(1).setProductType(ProductType.HYBRID_MIGRATION_RAMP);
        authoritative.getFirst().setCurrency("EUR");
        invalid();
        authoritative.getFirst().setCurrency("USD");
        context.getLineItems().get(1).setMetadata(Map.of());
        invalid();
    }

    @Test
    void malformedNumericMetadataAndCallerPolicyCannotAuthorizeCredit() {
        migration(0).put("bookValue", "10000");
        invalid();
        migration(0).put("bookValue", new BigDecimal("-1"));
        invalid();
        migration(0).put("bookValue", BigDecimal.ZERO);
        migration(1).put("bookValue", BigDecimal.ZERO);
        invalid();
    }

    @Test
    void repeatSimulationDoesNotMutateAuthoredInputOrCompoundCredit() throws Exception {
        String original = MAPPER.writeValueAsString(context);
        var first = service.simulate(context);
        var second = service.simulate(context);
        assertThat(first).usingRecursiveComparison().ignoringFields("executionTimeMs").isEqualTo(second);
        assertThat(first.getFinancialMetrics().getTcv()).isEqualByComparingTo("43800");
        assertThat(MAPPER.writeValueAsString(context)).isEqualTo(original);
        verify(audit, never()).save(any());
    }

    @Test
    void creditStaysOnFirstDecliningPeriodWhenActualOverlapStartsAtMonthSeven() {
        for (int line = 0; line < 2; line++) {
            migration(line).put("startMonth", 7);
            migration(line).put("months", 6);
        }
        context.getLineItems().get(1).setSchedule(List.of(step(1, 1, 6, 0, "50"),
                step(2, 7, 6, 10, "50"), step(3, 13, 24, 20, "60")));
        var result = service.simulate(context);
        assertThat(result.getLineResults().getFirst().getPricedPeriods().getFirst().getPeriodAmount()).isEqualByComparingTo("9000");
        assertThat(result.getFinancialMetrics().getTcv()).isEqualByComparingTo("40800");
        assertThat(result.getFinancialMetrics().getArr()).isEqualByComparingTo("12000");
        assertThat(result.getFinancialMetrics().getExitArr()).isEqualByComparingTo("14400");
    }

    @Test
    void disconnectedSecondOverlapCannotBeHiddenByMetadata() {
        for (int line = 0; line < 2; line++) migration(line).put("months", 6);
        context.getLineItems().getFirst().setSchedule(List.of(step(1, 1, 6, 10, "100"),
                step(2, 7, 6, 0, "100"), step(3, 13, 6, 10, "100"), step(4, 19, 18, 0, "100")));
        invalid();
    }

    @Test
    void strongerDiscountPolicyWinsButValidatedWindowDoesNotWaiveLaterDecline() {
        context.getLineItems().get(1).getSchedule().getFirst().setUnitPrice(new BigDecimal("40"));
        var higher = service.simulate(context);
        assertThat(higher.getApprovalTier()).isEqualTo("SALES_VP");
        assertThat(higher.getExplanationCodes()).contains("RAMP_HYBRID_WINDOW", "RAMP_DISCOUNT_THRESHOLD");
        context.getLineItems().get(1).getSchedule().getFirst().setUnitPrice(new BigDecimal("50"));
        context.getLineItems().getFirst().setSchedule(List.of(step(1, 1, 12, 10, "100"),
                step(2, 13, 6, 0, "100"), step(3, 19, 18, 0, "90")));
        var outside = service.simulate(context);
        assertThat(outside.getExplanationCodes()).contains("RAMP_HYBRID_WINDOW", "RAMP_STEP_DOWN");
    }

    @Test
    void malformedWholeMonthRoleAndPairIdentityFailClosed() throws Exception {
        String baseline = MAPPER.writeValueAsString(context);
        migration(0).put("startMonth", new BigDecimal("1.5"));
        invalid();
        context = MAPPER.readValue(baseline, PricingContext.class);
        migration(0).put("pairId", " ");
        invalid();
        context = MAPPER.readValue(baseline, PricingContext.class);
        migration(1).put("role", "DECLINING");
        invalid();
        context = MAPPER.readValue(baseline, PricingContext.class);
        migration(1).put("months", 11);
        invalid();
        context = MAPPER.readValue(baseline, PricingContext.class);
        context.getLineItems().get(1).setBillingFrequency("ONE_TIME");
        invalid();
    }

    @Test
    void ordinarySchedulesKeepExistingTotalsAndDisabledRampRemainsDisabled() {
        service.setRampEnabled(false);
        assertThatThrownBy(() -> service.execute(context)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RAMP_NOT_SUPPORTED");
        service.setRampEnabled(true);
        for (var line : context.getLineItems()) line.setMetadata(Map.of());
        var ordinary = service.simulate(context);
        assertThat(ordinary.getFinancialMetrics().getTcv()).isEqualByComparingTo("46800");
        assertThat(ordinary.getExplanationCodes()).doesNotContain("RAMP_HYBRID_WINDOW");
        assertThat(MAPPER.valueToTree(ordinary).path("lineResults").get(0).path("pricedPeriods").get(0)
                .has("migrationCredit")).isFalse();
    }

    @Test
    void independentlyValidatedPairsDoNotShareOrCompoundCredit() throws Exception {
        var secondDecline = MAPPER.readValue(MAPPER.writeValueAsString(context.getLineItems().getFirst()), PricingLineRequest.class);
        var secondRise = MAPPER.readValue(MAPPER.writeValueAsString(context.getLineItems().get(1)), PricingLineRequest.class);
        secondDecline.setProductExternalId("R4-SYNTHETIC-ONPREM-SUPPORT-002");
        secondRise.setProductExternalId("R4-SYNTHETIC-HYBRID-RISING-002");
        @SuppressWarnings("unchecked") var secondDeclineInput = (Map<String, Object>) secondDecline.getMetadata().get("hybridMigration");
        @SuppressWarnings("unchecked") var secondRiseInput = (Map<String, Object>) secondRise.getMetadata().get("hybridMigration");
        secondDeclineInput.put("pairId", "R4-SYNTHETIC-PAIR-002");
        secondRiseInput.put("pairId", "R4-SYNTHETIC-PAIR-002");
        context.setLineItems(List.of(context.getLineItems().getFirst(), context.getLineItems().get(1), secondDecline, secondRise));
        for (int index = 0; index < 2; index++) {
            var clone = MAPPER.readValue(MAPPER.writeValueAsString(authoritative.get(index)), PricingProduct.class);
            clone.setExternalId(context.getLineItems().get(index + 2).getProductExternalId());
            authoritative.add(clone);
        }
        var result = service.simulate(context);
        assertThat(result.getFinancialMetrics().getTcv()).isEqualByComparingTo("87600");
        assertThat(result.getLineResults().get(2).getPricedPeriods().getFirst().getPeriodAmount()).isEqualByComparingTo("9000");
        verify(products, times(1)).findByExternalIdInAndDeletedFalse(anyCollection());
    }

    @Test
    void firstPeriodCapLeavesLaterDecliningAmountsAndUnusedCreditIntact() {
        for (int line = 0; line < 2; line++) migration(line).put("months", 3);
        migration(0).put("bookValue", new BigDecimal("6000"));
        context.getLineItems().getFirst().setSchedule(List.of(step(1, 1, 1, 10, "100"),
                step(2, 2, 2, 10, "100"), step(3, 4, 33, 0, "100")));
        context.getLineItems().get(1).setSchedule(List.of(step(1, 1, 3, 10, "50"), step(2, 4, 33, 20, "60")));
        var result = service.simulate(context);
        var decline = result.getLineResults().getFirst();
        assertThat(decline.getPricedPeriods().getFirst().getPeriodAmount()).isEqualByComparingTo("0");
        assertThat(decline.getPricedPeriods().get(1).getPeriodAmount()).isEqualByComparingTo("2000");
        var audit = MAPPER.valueToTree(decline.getMetadata()).path("migrationCreditAudit");
        assertThat(audit.path("requestedCredit").decimalValue()).isEqualByComparingTo("1800");
        assertThat(audit.path("effectiveCredit").decimalValue()).isEqualByComparingTo("1000");
        assertThat(audit.path("unusedCredit").decimalValue()).isEqualByComparingTo("800");
        assertThat(result.getFinancialMetrics().getTcv()).isEqualByComparingTo("43100");
    }

    @Test
    void resolvedPriceBookCurrencyCannotConflictWithAuthoritativeProductAndQuoteCurrency() {
        Map<String, ProductEntryResolveDto> entries = new HashMap<>();
        for (var product : authoritative) entries.put(product.getExternalId(), ProductEntryResolveDto.builder()
                .entryMatched(true).listPrice(product.getListPrice()).currency("EUR").build());
        when(pricebook.resolveBatch(anyString(), anyList())).thenReturn(ResolveBatchResponse.builder()
                .entries(entries).priceBookCurrency("EUR").build());
        invalid();
    }

    @Test
    void decliningQuantityAndRateInsideValidatedWindowAreNotOrdinaryStepDowns() {
        context.getLineItems().getFirst().setSchedule(List.of(step(1, 1, 6, 10, "100"),
                step(2, 7, 6, 8, "95"), step(3, 13, 24, 0, "95")));
        var result = service.simulate(context);
        assertThat(result.getApprovalTier()).isEqualTo("DEAL_DESK");
        assertThat(result.getExplanationCodes()).contains("RAMP_HYBRID_WINDOW").doesNotContain("RAMP_STEP_DOWN");
        assertThat(result.getFinancialMetrics().getTcv()).isEqualByComparingTo("42360");
    }

    @Test
    void callerLineTypeCannotSelectDifferentPricingForAuthoritativeMigrationProducts() throws Exception {
        String baseline = MAPPER.writeValueAsString(context);
        for (int line = 0; line < 2; line++) {
            context = MAPPER.readValue(baseline, PricingContext.class);
            context.getLineItems().get(line).setLineItemType("SAAS_SEAT_NAMED");
            invalid();
        }
        context = MAPPER.readValue(baseline, PricingContext.class);
        context.getLineItems().getFirst().setLineItemType(null);
        invalid();
        verify(audit, never()).save(any());
    }

    @Test
    void bulkSimulationUsesTheSameCreditAndRunRateContractWithoutAudit() {
        var bulk = service.simulateBulk(List.of(context, context));
        assertThat(bulk.getTotalRequests()).isEqualTo(2);
        assertThat(bulk.getSuccessfulRequests()).isEqualTo(2);
        assertThat(bulk.getResults()).allSatisfy(result -> {
            assertThat(result.getFinancialMetrics().getTcv()).isEqualByComparingTo("43800");
            assertThat(result.getFinancialMetrics().getArr()).isEqualByComparingTo("18000");
            assertThat(result.getLineResults().getFirst().getPricedPeriods().getFirst().getMigrationCredit())
                    .isEqualByComparingTo("3000");
        });
        verify(products, times(2)).findByExternalIdInAndDeletedFalse(anyCollection());
        verify(audit, never()).save(any());
    }

    private static com.opencrm.common.pricing.RampStep step(int seq, int start, int months, int quantity, String unit) {
        return com.opencrm.common.pricing.RampStep.builder().sequence(seq).startMonth(start).months(months).quantity(quantity)
                .priceBasis(com.opencrm.common.pricing.RampPriceBasis.ABSOLUTE).unitPrice(new BigDecimal(unit)).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> migration(int line) {
        return (Map<String, Object>) context.getLineItems().get(line).getMetadata().get("hybridMigration");
    }

    private void invalid() {
        assertThatThrownBy(() -> service.execute(context)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RAMP_SCHEDULE_INVALID");
    }
}
