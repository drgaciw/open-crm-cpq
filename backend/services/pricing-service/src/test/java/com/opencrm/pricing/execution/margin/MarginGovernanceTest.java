package com.opencrm.pricing.execution.margin;

import com.opencrm.pricing.cost.CostBasisLookup;
import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import com.opencrm.rules.drools.DroolsRuleCompiler;
import com.opencrm.rules.drools.DroolsRulesEngine;
import com.opencrm.rules.drools.DroolsSessionFactory;
import com.opencrm.rules.engine.RulesEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.opencrm.pricing.execution.margin.CostBasisMarginEvaluatorTest.CLOCK;
import static com.opencrm.pricing.execution.margin.CostBasisMarginEvaluatorTest.context;
import static com.opencrm.pricing.execution.margin.CostBasisMarginEvaluatorTest.cost;
import static com.opencrm.pricing.execution.margin.CostBasisMarginEvaluatorTest.flat;
import static com.opencrm.pricing.execution.margin.CostBasisMarginEvaluatorTest.period;
import static com.opencrm.pricing.execution.margin.CostBasisMarginEvaluatorTest.ramp;
import static org.assertj.core.api.Assertions.assertThat;

/** AC-16 and the fail-closed rule, evaluated through the rules port with approval-margin-exception.drl. */
@Tag("unit")
class MarginGovernanceTest {

    private final RulesEngine port = new DroolsRulesEngine(new DroolsSessionFactory(new DroolsRuleCompiler()));
    private final CostBasisLookup seatCost = (product, effectiveOn, pricedOn) ->
            Optional.of(cost(product, "10.48", "USD", LocalDate.of(2026, 10, 1), 1));

    @Test
    @DisplayName("AC-16: a period under the 35% floor routes to DEAL_DESK and the worst period governs, by name")
    void ac16WorstPeriodGoverns() {
        // Margins: p1 0.7380, p2 0.3013 (15.00), p3 0.3450 (16.00). Two periods are under the floor.
        PricingResult result = result(ramp("PROD-SEAT", period(1, 1, 10, "40.00"), period(2, 13, 10, "15.00"),
                period(3, 25, 10, "16.00")));
        govern(result, seatCost);

        assertThat(result.getApprovalState()).isEqualTo("PENDING");
        assertThat(result.getApprovalTier()).isEqualTo("DEAL_DESK");
        assertThat(result.getExplanationCodes()).contains("MARGIN_BELOW_FLOOR");
        Map<?, ?> governing = marginGovernance(result, 0);
        assertThat(governing.get("governingPeriod")).isEqualTo(2);
        assertThat((BigDecimal) governing.get("marginFraction")).isEqualByComparingTo("0.3013");
        assertThat((BigDecimal) governing.get("marginPercent")).isEqualByComparingTo("30.13");
        assertThat(governing.get("approvalTier")).isEqualTo("DEAL_DESK");
        assertThat((List<?>) governing.get("reasons")).containsExactly("MARGIN_BELOW_FLOOR");
        assertThat(governing.get("productExternalId")).isEqualTo("PROD-SEAT");
    }

    @Test
    @DisplayName("exactly 35% is at the floor, not under it, so it is not routed")
    void exactlyAtFloorIsApproved() {
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) ->
                Optional.of(cost(product, "6.50", "USD", LocalDate.of(2026, 10, 1), 1));
        PricingResult result = result(flat("PROD-X", 1, "10.00"));
        MarginAssessment assessment = govern(result, lookup);

        assertThat(assessment.getQuoteMarginFraction()).isEqualByComparingTo("0.3500");
        assertThat(result.getApprovalState()).isEqualTo("APPROVED");
        assertThat(result.getApprovalTier()).isNull();
        assertThat(result.getExplanationCodes()).doesNotContain("MARGIN_BELOW_FLOOR");
        assertThat(result.getLineResults().getFirst().getMetadata()).doesNotContainKey("marginGovernance");
        assertThat(result.getLineResults().getFirst().getMetadata()).containsKey("margin");
    }

    @Test
    @DisplayName("just under 35% (0.3499) is routed")
    void justUnderFloorIsRouted() {
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) ->
                Optional.of(cost(product, "6.5001", "USD", LocalDate.of(2026, 10, 1), 1));
        PricingResult result = result(flat("PROD-X", 1, "10.00"));
        govern(result, lookup);

        assertThat(result.getApprovalTier()).isEqualTo("DEAL_DESK");
        assertThat(result.getExplanationCodes()).contains("MARGIN_BELOW_FLOOR");
        Map<?, ?> governing = marginGovernance(result, 0);
        assertThat(governing.get("governingPeriod")).isNull();
        assertThat((BigDecimal) governing.get("marginFraction")).isEqualByComparingTo("0.3499");
    }

    @Test
    @DisplayName("a missing cost routes to DEAL_DESK naming the product, with no margin value")
    void missingCostRoutesAndNamesProduct() {
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) -> "PROD-SEAT".equals(product)
                ? seatCost.find(product, effectiveOn, pricedOn) : Optional.empty();
        PricingResult result = result(flat("PROD-SEAT", 1, "40.00"), flat("PROD-NEW", 1, "100.00"));
        govern(result, lookup);

        assertThat(result.getApprovalState()).isEqualTo("PENDING");
        assertThat(result.getApprovalTier()).isEqualTo("DEAL_DESK");
        assertThat(result.getExplanationCodes()).contains("MARGIN_COST_MISSING: PROD-NEW");
        assertThat(result.getExplanationCodes()).noneMatch(code -> code.contains("PROD-SEAT"));
        Map<?, ?> governing = marginGovernance(result, 1);
        assertThat(governing.get("marginFraction")).isNull();
        assertThat(governing.get("productExternalId")).isEqualTo("PROD-NEW");
        assertThat((List<?>) governing.get("reasons")).containsExactly("MARGIN_COST_MISSING: PROD-NEW");
        assertThat(result.getLineResults().get(0).getMetadata()).doesNotContainKey("marginGovernance");
    }

    @Test
    @DisplayName("a missing period cost governs a ramp line ahead of any computed margin")
    void missingPeriodGoverns() {
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) -> effectiveOn.isBefore(LocalDate.of(2027, 10, 1))
                ? seatCost.find(product, effectiveOn, pricedOn) : Optional.empty();
        PricingResult result = result(ramp("PROD-SEAT", period(1, 1, 10, "15.00"), period(2, 13, 10, "40.00")));
        govern(result, lookup);

        Map<?, ?> governing = marginGovernance(result, 0);
        assertThat(governing.get("governingPeriod")).isEqualTo(2);
        assertThat((List<?>) governing.get("reasons")).containsExactly("MARGIN_COST_MISSING: PROD-SEAT");
        assertThat(result.getExplanationCodes()).contains("MARGIN_COST_MISSING: PROD-SEAT");
    }

    @Test
    @DisplayName("currency mismatch routes and names both currencies")
    void currencyMismatchRoutes() {
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) ->
                Optional.of(cost(product, "10.48", "EUR", LocalDate.of(2026, 10, 1), 1));
        PricingResult result = result(flat("PROD-SEAT", 1, "40.00"));
        govern(result, lookup);
        assertThat(result.getApprovalTier()).isEqualTo("DEAL_DESK");
        assertThat(result.getExplanationCodes())
                .contains("MARGIN_COST_CURRENCY_MISMATCH: PROD-SEAT (cost EUR, price USD)");
    }

    @Test
    @DisplayName("m1: a zero-net line is margin-not-applicable: no routing, no fact, the reason is in the trace")
    void zeroNetDoesNotRoute() {
        PricingResult result = result(flat("PROD-SEAT", 1, "40.00"), flat("PROD-FREE", 1, "0.00"));
        MarginAssessment assessment = govern(result, seatCost);

        assertThat(result.getApprovalState()).isEqualTo("APPROVED");
        assertThat(result.getApprovalTier()).isNull();
        assertThat(result.getExplanationCodes()).noneMatch(code -> code.startsWith("MARGIN_"));
        assertThat(result.getLineResults().get(1).getMetadata()).doesNotContainKey("marginGovernance");
        LineMargin free = (LineMargin) result.getLineResults().get(1).getMetadata().get("margin");
        assertThat(free.getNotApplicableReason()).isEqualTo("MARGIN_NOT_APPLICABLE_ZERO_NET");
        assertThat(assessment.getQuoteMarginFraction()).isEqualByComparingTo("0.7380");
    }

    @Test
    @DisplayName("m1: a negative net still routes to DEAL_DESK as MARGIN_NET_NOT_POSITIVE")
    void negativeNetRoutes() {
        PricingResult result = result(flat("PROD-SEAT", 1, "-5.00"));
        govern(result, seatCost);

        assertThat(result.getApprovalState()).isEqualTo("PENDING");
        assertThat(result.getApprovalTier()).isEqualTo("DEAL_DESK");
        assertThat(result.getExplanationCodes()).contains("MARGIN_NET_NOT_POSITIVE: PROD-SEAT");
        assertThat((List<?>) marginGovernance(result, 0).get("reasons"))
                .containsExactly("MARGIN_NET_NOT_POSITIVE: PROD-SEAT");
    }

    @Test
    @DisplayName("a higher tier already set by discount governance is kept")
    void higherTierKept() {
        PricingResult result = result(flat("PROD-SEAT", 1, "15.00"));
        result.setApprovalTier("SALES_VP");
        result.setApprovalState("PENDING");
        govern(result, seatCost);
        assertThat(result.getApprovalTier()).isEqualTo("SALES_VP");
        assertThat(result.getExplanationCodes()).contains("MARGIN_BELOW_FLOOR");
    }

    @Test
    @DisplayName("caller-supplied margin metadata is replaced by the server's evidence")
    void callerCannotForgeMargin() {
        PricingLineResult line = flat("PROD-SEAT", 1, "15.00");
        line.setMetadata(new java.util.HashMap<>(Map.of("margin", "forged", "marginGovernance", "forged")));
        PricingResult result = result(line);
        govern(result, seatCost);
        assertThat(result.getLineResults().getFirst().getMetadata().get("margin")).isInstanceOf(LineMargin.class);
        assertThat(marginGovernance(result, 0).get("reasons")).isNotEqualTo("forged");

        PricingLineResult approved = flat("PROD-SEAT", 1, "40.00");
        approved.setMetadata(new java.util.HashMap<>(Map.of("marginGovernance", "forged")));
        PricingResult clean = result(approved);
        govern(clean, seatCost);
        assertThat(clean.getLineResults().getFirst().getMetadata()).doesNotContainKey("marginGovernance");
    }

    @Test
    @DisplayName("the 40.0 placeholder is never offered to the rule: the fact carries the fraction")
    void ruleSeesFractionNotPlaceholder() {
        PricingResult result = result(flat("PROD-SEAT", 1, "40.00"));
        result.getFinancialMetrics().setEstimatedMarginPct(new BigDecimal("40.0"));
        govern(result, (product, effectiveOn, pricedOn) -> Optional.of(cost(product, "39.00", "USD", LocalDate.of(2026, 10, 1), 1)));
        // 2.5% margin: if the rule had read 40.0 it would not fire.
        assertThat(result.getExplanationCodes()).contains("MARGIN_BELOW_FLOOR");
    }

    @Test
    @DisplayName("the DRL ignores an absent margin, passes exactly 0.35 and routes 0.3499")
    void drlFloorAndNullGuard() throws Exception {
        String drl;
        try (var in = getClass().getResourceAsStream("/rules/approval-margin-exception.drl")) {
            drl = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var rule = com.opencrm.rules.engine.RuleDefinition.builder().id("margin").name("margin").content(drl).build();
        com.opencrm.rules.model.QuoteFact absent = drlFact(null);
        com.opencrm.rules.model.QuoteFact atFloor = drlFact(new BigDecimal("0.35"));
        com.opencrm.rules.model.QuoteFact under = drlFact(new BigDecimal("0.3499"));
        var context = com.opencrm.rules.engine.RuleExecutionContext.builder()
                .engineType(com.opencrm.rules.engine.RulesEngineType.DROOLS).build();
        context.getFacts().addAll(List.of(absent, atFloor, under));
        assertThat(port.execute(context, List.of(rule)).isSuccess()).isTrue();
        assertThat(absent.getString("explanationCode")).isNull();
        assertThat(atFloor.getString("explanationCode")).isNull();
        assertThat(under.getString("explanationCode")).isEqualTo("MARGIN_BELOW_FLOOR");
        assertThat(under.getString("approvalTier")).isEqualTo("DEAL_DESK");
    }

    private static com.opencrm.rules.model.QuoteFact drlFact(BigDecimal fraction) {
        var fact = com.opencrm.rules.model.QuoteFact.builder()
                .basePrice(BigDecimal.TEN).finalPrice(BigDecimal.TEN).build();
        if (fraction != null) fact.setDecimal("estimatedMarginPct", fraction);
        return fact;
    }

    private MarginAssessment govern(PricingResult result, CostBasisLookup lookup) {
        List<String> products = result.getLineResults().stream().map(PricingLineResult::getProductExternalId).toList();
        MarginAssessment assessment = new CostBasisMarginEvaluator(lookup, CLOCK)
                .assess(context("USD", products.toArray(String[]::new)), result.getLineResults(), null);
        new MarginGovernance().evaluate(result, assessment, port, "USD");
        return assessment;
    }

    private static PricingResult result(PricingLineResult... lines) {
        return PricingResult.builder().approvalState("APPROVED").explanationCodes(new ArrayList<>())
                .financialMetrics(FinancialMetrics.builder().build())
                .lineResults(List.of(lines)).build();
    }

    private static Map<?, ?> marginGovernance(PricingResult result, int line) {
        return (Map<?, ?>) result.getLineResults().get(line).getMetadata().get("marginGovernance");
    }
}
