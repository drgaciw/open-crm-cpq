package com.opencrm.pricing.ramp;

import com.opencrm.pricing.product.PricingProduct;
import com.opencrm.pricing.waterfall.*;
import com.opencrm.rules.drools.*;
import com.opencrm.rules.engine.RuleDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PeriodGovernanceTest {
    private final DroolsRulesEngine port = new DroolsRulesEngine(new DroolsSessionFactory(new DroolsRuleCompiler()));

    @Test void ac05WorstPeriodRoutesSalesVpAndIgnoresPlaceholderMargin() {
        PricingResult result = result("34", "45", "50", 50, 50, 50);
        new PeriodGovernance().evaluate(context(), result, port, Map.of(), List.of());
        assertThat(result.getApprovalTier()).isEqualTo("SALES_VP");
        assertThat(result.getApprovalState()).isEqualTo("PENDING");
        Map<?, ?> governing = governance(result);
        assertThat(governing.get("governingPeriod")).isEqualTo(1);
        assertThat((BigDecimal) governing.get("discountPercent")).isEqualByComparingTo("32");
        assertThat(result.getExplanationCodes()).doesNotContain("MARGIN_BELOW_FLOOR");
    }

    @Test void ac15QuantityStepDownNeedsApprovalAndCallerCannotWaiveIt() {
        PricingContext context = context();
        context.getLineItems().getFirst().setMetadata(Map.of("stepDownAllowed", true));
        PricingResult result = result("50", "50", "50", 150, 100, 100);
        new PeriodGovernance().evaluate(context, result, port, Map.of(), List.of());
        assertThat(result.getApprovalTier()).isEqualTo("DEAL_DESK");
        assertThat(governance(result).get("stepDown")).isEqualTo(true);
        assertThat(governance(result).get("governingPeriod")).isEqualTo(2);
    }

    @Test void authoritativeProductCanPermitStepDown() {
        PricingResult result = result("50", "50", "50", 150, 100, 100);
        PricingProduct product = PricingProduct.builder().metadata("{\"rampStepDownAllowed\":true}").build();
        new PeriodGovernance().evaluate(context(), result, port, Map.of("P-1", product), List.of());
        assertThat(result.getApprovalState()).isEqualTo("APPROVED");
    }

    @Test void laterWorstDiscountRetainsEarlierStepDownReason() {
        PricingResult result = result("50", "45", "34", 150, 100, 100);
        new PeriodGovernance().evaluate(context(), result, port, Map.of(), List.of());
        assertThat(result.getApprovalTier()).isEqualTo("SALES_VP");
        assertThat(governance(result).get("governingPeriod")).isEqualTo(3);
        assertThat(governance(result).get("stepDown")).isEqualTo(true);
        assertThat((List<String>) governance(result).get("reasons")).contains("RAMP_STEP_DOWN");
    }

    @Test void configuredAcvFloorSeesAggregateMetricAndRoutesRequiredCommitment() throws Exception {
        PricingResult result = result("50", "50", "50", 50, 50, 50);
        result.getFinancialMetrics().setTcv(new BigDecimal("60000"));
        result.getFinancialMetrics().setAcv(new BigDecimal("60000"));
        new PeriodGovernance().evaluate(context(), result, port, Map.of(), List.of(rule("acv-minimum-commitment.drl")));
        assertThat(result.getApprovalTier()).isEqualTo("DEAL_DESK");
        assertThat((List<String>) governance(result).get("reasons"))
                .anyMatch(reason -> reason.contains("required 100000 USD, scheduled 60000"));
        assertThat(result.getFinancialMetrics().getTcv()).isEqualByComparingTo("60000");
        result = result("50", "50", "50", 50, 50, 50);
        result.getFinancialMetrics().setTcv(new BigDecimal("120000"));
        result.getFinancialMetrics().setAcv(new BigDecimal("120000"));
        new PeriodGovernance().evaluate(context(), result, port, Map.of(), List.of(rule("acv-minimum-commitment.drl")));
        assertThat(result.getApprovalState()).isEqualTo("APPROVED");
    }

    @Test void configuredTcvTierSeesAggregateMetric() throws Exception {
        PricingResult result = result("50", "50", "50", 50, 50, 50);
        result.getFinancialMetrics().setTcv(new BigDecimal("500000"));
        result.getFinancialMetrics().setAcv(new BigDecimal("166666.67"));
        new PeriodGovernance().evaluate(context(), result, port, Map.of(), List.of(rule("tcv-pricing-tiers.drl")));
        assertThat((List<String>) governance(result).get("reasons"))
                .anyMatch(reason -> reason.contains("required 550000 USD, scheduled 500000"));
        assertThat(result.getApprovalTier()).isEqualTo("DEAL_DESK");
    }

    @Test
    void renewalUpliftRunsForRenewalAndDoesNotRepricePeriods() {
        PricingResult priced = result("34", "45", "50", 50, 50, 50);
        new PeriodGovernance().evaluate(renewalContext(80, null), priced, port, Map.of(), List.of());
        assertThat(priced.getApprovalTier()).isEqualTo("SALES_VP");
        assertThat((List<String>) governance(priced).get("reasons"))
                .contains("RAMP_DISCOUNT_THRESHOLD").doesNotContain("RENEWAL_UPLIFT_POLICY");
        assertThat(priced.getLineResults().getFirst().getPricedPeriods())
                .extracting(PricedPeriod::getNetUnitPrice)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("34"), new BigDecimal("45"), new BigDecimal("50"));
        Map<String, Object> metadata = priced.getLineResults().getFirst().getMetadata();
        assertThat(metadata.get("renewalUpliftPct")).isEqualTo("0.03");
        assertThat(metadata.get("renewalUpliftSource")).isEqualTo("RENEWAL_UPLIFT_POLICY");
        assertThat(metadata.get("retentionOffer")).isEqualTo("ENABLE_12M_PRICE_PROTECTION");
    }

    @Test
    void renewalUpliftAtChurnBoundaryStaysAtEightPercent() {
        PricingResult priced = result("50", "50", "50", 50, 50, 50);
        new PeriodGovernance().evaluate(renewalContext(70, null), priced, port, Map.of(), List.of());
        Map<String, Object> metadata = priced.getLineResults().getFirst().getMetadata();
        assertThat(metadata.get("renewalUpliftPct")).isEqualTo("0.08");
        assertThat(metadata).doesNotContainKey("retentionOffer");
        assertThat(priced.getApprovalState()).isEqualTo("APPROVED");
    }

    @Test
    void renewalWithoutChurnUsesEightPercent() {
        PricingResult priced = result("50", "50", "50", 50, 50, 50);
        new PeriodGovernance().evaluate(renewalContext(null, null), priced, port, Map.of(), List.of());
        assertThat(priced.getLineResults().getFirst().getMetadata().get("renewalUpliftPct")).isEqualTo("0.08");
    }

    @Test
    void callerSuppliedUpliftBeatsTheDefaultRule() {
        PricingResult priced = result("50", "50", "50", 50, 50, 50);
        new PeriodGovernance().evaluate(renewalContext(80, Map.of("renewalUpliftPct", new BigDecimal("0.05"))),
                priced, port, Map.of(), List.of());
        Map<String, Object> metadata = priced.getLineResults().getFirst().getMetadata();
        assertThat(metadata.get("renewalUpliftSource")).isEqualTo("CALLER");
        assertThat(metadata.get("renewalUpliftPct")).isEqualTo("0.05");
        assertThat(metadata).doesNotContainKey("retentionOffer");
    }

    @Test
    void callerSuppliedFlagBlocksTheDefaultWithoutInventingAFraction() {
        PricingResult priced = result("50", "50", "50", 50, 50, 50);
        new PeriodGovernance().evaluate(renewalContext(80, Map.of("renewalUpliftSupplied", true)),
                priced, port, Map.of(), List.of());
        Map<String, Object> metadata = priced.getLineResults().getFirst().getMetadata();
        assertThat(metadata.get("renewalUpliftSource")).isEqualTo("CALLER");
        assertThat(metadata).doesNotContainKey("renewalUpliftPct");
        assertThat(metadata).doesNotContainKey("retentionOffer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "AMENDMENT", "EXPANSION", "NEW_BUSINESS", "EXISTING_BUSINESS",
            "UPSELL", "CROSS_SELL", "renewal", "EXTENSION", " RENEWAL", ""})
    void otherDealTypesDoNotRunRenewalUplift(String dealType) {
        PricingContext context = context();
        context.setMetadata(Map.of("dealType", dealType, "churnScore", 80));
        PricingResult priced = result("50", "50", "50", 50, 50, 50);
        new PeriodGovernance().evaluate(context, priced, port, Map.of(), List.of());
        assertThat(priced.getLineResults().getFirst().getMetadata()).doesNotContainKey("renewalUpliftPct");
        assertThat(priced.getApprovalState()).isEqualTo("APPROVED");
    }

    @Test
    void governanceFactCarriesRenewalDealType() {
        String probe = """
                package com.opencrm.pricing.rules;
                import com.opencrm.rules.model.QuoteFact;
                rule "Probe renewal deal type"
                when
                    $quote: QuoteFact()
                    eval("RENEWAL".equals($quote.getString("dealType")))
                then
                    $quote.setString("approvalTier", "CFO");
                end
                """;
        PricingResult priced = result("50", "50", "50", 50, 50, 50);
        new PeriodGovernance().evaluate(renewalContext(null, null), priced, port, Map.of(),
                List.of(RuleDefinition.builder().id("probe").name("probe").content(probe).build()));
        assertThat(priced.getApprovalTier()).isEqualTo("CFO");
        assertThat(priced.getLineResults().getFirst().getMetadata().get("renewalUpliftSource"))
                .isEqualTo("RENEWAL_UPLIFT_POLICY");
    }

    private PricingContext renewalContext(Integer churn, Map<String, Object> extra) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("dealType", "RENEWAL");
        if (churn != null) metadata.put("churnScore", churn);
        if (extra != null) metadata.putAll(extra);
        return PricingContext.builder().quoteExternalId("1").durationInMonths(36).currency("USD")
                .metadata(metadata)
                .lineItems(List.of(PricingLineRequest.builder().productExternalId("P-1").build())).build();
    }

    private RuleDefinition rule(String filename) throws Exception {
        try (var stream = getClass().getResourceAsStream("/rules/" + filename)) {
            return RuleDefinition.builder().id(filename).name(filename)
                    .content(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).build();
        }
    }
    private PricingContext context() {
        return PricingContext.builder().quoteExternalId("1").durationInMonths(36).currency("USD")
                .lineItems(List.of(PricingLineRequest.builder().productExternalId("P-1").build())).build();
    }
    private Map<?, ?> governance(PricingResult result) {
        return (Map<?, ?>) result.getLineResults().getFirst().getMetadata().get("rampGovernance");
    }
    private PricingResult result(String first, String second, String third, int q1, int q2, int q3) {
        List<PricedPeriod> periods = new ArrayList<>();
        String[] prices = {first, second, third}; int[] quantities = {q1, q2, q3};
        for (int i=0; i<3; i++) {
            BigDecimal unit = new BigDecimal(prices[i]);
            periods.add(PricedPeriod.builder().sequence(i+1).startMonth(i*12+1).months(12).quantity(quantities[i])
                    .netUnitPrice(unit).netMonthly(unit.multiply(BigDecimal.valueOf(quantities[i])))
                    .periodAmount(unit.multiply(BigDecimal.valueOf(quantities[i]*12)))
                    .discountRate(new BigDecimal("50").subtract(unit).divide(new BigDecimal("50"))).build());
        }
        return PricingResult.builder().approvalState("APPROVED").explanationCodes(new ArrayList<>())
                .financialMetrics(FinancialMetrics.builder().tcv(new BigDecimal("77400")).acv(new BigDecimal("25800"))
                        .estimatedMarginPct(new BigDecimal("40")).build())
                .lineResults(List.of(PricingLineResult.builder().productExternalId("P-1")
                        .listPrice(new BigDecimal("50")).pricedPeriods(periods).build())).build();
    }
}
