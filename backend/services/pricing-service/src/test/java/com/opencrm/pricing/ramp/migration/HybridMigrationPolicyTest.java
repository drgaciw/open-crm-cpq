package com.opencrm.pricing.ramp.migration;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.pricing.RampPriceBasis;
import com.opencrm.common.pricing.RampStep;
import com.opencrm.pricing.execution.AcvVersion;
import com.opencrm.pricing.execution.AcvVersionProperties;
import com.opencrm.pricing.execution.MetricsCalculator;
import com.opencrm.pricing.product.PricingProduct;
import com.opencrm.pricing.product.ProductType;
import com.opencrm.pricing.ramp.PricedPeriod;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingLineRequest;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class HybridMigrationPolicyTest {
    private final HybridMigrationPolicy policy = new HybridMigrationPolicy(12);

    @Test
    void oneTimeCreditPreservesRecurringRatesAndGrossAmountAcrossTheSharedContract() {
        var context = context(12, 10, new BigDecimal("10000"));
        var lines = pricedLines();
        var plan = policy.prepare(context, products());
        policy.apply(plan, lines);

        var first = lines.getFirst().getPricedPeriods().getFirst();
        assertThat(first.getGrossPeriodAmount()).isEqualByComparingTo("12000.00");
        assertThat(first.getMigrationCredit()).isEqualByComparingTo("3000.00");
        assertThat(first.getPeriodAmount()).isEqualByComparingTo("9000.00");
        assertThat(first.getNetUnitPrice()).isEqualByComparingTo("100.0000");
        assertThat(first.getNetMonthly()).isEqualByComparingTo("1000.00");
        var finalDeclining = lines.getFirst().getPricedPeriods().get(1);
        assertThat(finalDeclining.getMigrationCredit()).isNull();
        assertThat(finalDeclining.getGrossPeriodAmount()).isNull();
        assertThat(finalDeclining.getPeriodAmount()).isEqualByComparingTo("0.00");
        assertThat(lines.get(1).getPricedPeriods().getLast().getNetUnitPrice()).isEqualByComparingTo("60.00");
        assertThat(lines.get(1).getPricedPeriods()).allSatisfy(period -> {
            assertThat(period.getMigrationCredit()).isNull();
            assertThat(period.getGrossPeriodAmount()).isNull();
        });

        var properties = new AcvVersionProperties();
        properties.setAcvVersion(AcvVersion.TERM_YEARS);
        var metrics = new MetricsCalculator(properties).calculate(PricingResult.builder().lineResults(lines).build(), 36);
        assertThat(metrics.getTcv()).isEqualByComparingTo("43800.00");
        assertThat(metrics.getAcv()).isEqualByComparingTo("14600.00");
        assertThat(metrics.getArr()).isEqualByComparingTo("18000.00");
        assertThat(metrics.getExitArr()).isEqualByComparingTo("14400.00");
    }

    @Test
    void actualTwelveMonthOverlapAcceptedButThirteenMonthsRejected() {
        assertThat(policy.prepare(context(12, 10, BigDecimal.ZERO), products())).isNotNull();
        assertThatThrownBy(() -> policy.prepare(context(13, 10, BigDecimal.ZERO), products()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void metadataWindowCannotHidePositiveQuantityAfterItsDeclaredEnd() {
        var context = context(12, 10, BigDecimal.ZERO);
        context.getLineItems().getFirst().getSchedule().get(1).setQuantity(1);
        assertThatThrownBy(() -> policy.prepare(context, products())).isInstanceOf(BusinessException.class);
    }

    @Test
    void freePriceDoesNotRemoveEntitlement() {
        var context = context(12, 10, BigDecimal.ZERO);
        var late = context.getLineItems().getFirst().getSchedule().get(1);
        late.setQuantity(1);
        late.setUnitPrice(BigDecimal.ZERO);
        assertThatThrownBy(() -> policy.prepare(context, products())).isInstanceOf(BusinessException.class);
    }

    @Test
    void callerRoleCannotForgeTheAuthoritativeHybridProductType() {
        var forged = Map.of("maintenance", product("maintenance", ProductType.ONPREM_SUPPORT),
                "saas", product("saas", ProductType.SAAS_SEAT_NAMED));
        assertThatThrownBy(() -> policy.prepare(context(12, 10, BigDecimal.ZERO), forged))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void missingPartnerAndMismatchedProductCurrencyFailClosed() {
        var missing = context(12, 10, BigDecimal.ZERO);
        missing.setLineItems(List.of(missing.getLineItems().getFirst()));
        assertThatThrownBy(() -> policy.prepare(missing, products())).isInstanceOf(BusinessException.class);
        var mismatchedProducts = products();
        mismatchedProducts.get("maintenance").setCurrency("EUR");
        assertThatThrownBy(() -> policy.prepare(context(12, 10, BigDecimal.ZERO), mismatchedProducts))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void repeatExecutionFromAuthoredInputDoesNotCompoundTheCredit() {
        var context = context(12, 10, new BigDecimal("10000"));
        var first = pricedLines();
        var second = pricedLines();
        policy.apply(policy.prepare(context, products()), first);
        policy.apply(policy.prepare(context, products()), second);
        assertThat(first).usingRecursiveComparison().isEqualTo(second);
        assertThat(context.getLineItems().getFirst().getSchedule().getFirst().getUnitPrice())
                .isEqualByComparingTo("100");
    }

    @Test
    void configuredWindowRestrictsActualEntitlementRatherThanOnlyTheMetadata() {
        var restricted = new HybridMigrationPolicy(6);
        assertThat(restricted.prepare(context(6, 10, BigDecimal.ZERO), products())).isNotNull();
        assertThatThrownBy(() -> restricted.prepare(context(7, 10, BigDecimal.ZERO), products()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void alreadyCreditedOutputIsRejectedWithoutDestroyingItsAuditEvidence() {
        var plan = policy.prepare(context(12, 10, new BigDecimal("10000")), products());
        var lines = pricedLines();
        policy.apply(plan, lines);
        var audit = lines.getFirst().getMetadata().get("migrationCreditAudit");
        assertThatThrownBy(() -> policy.apply(plan, lines)).isInstanceOf(BusinessException.class);
        assertThat(lines.getFirst().getMetadata().get("migrationCreditAudit")).isEqualTo(audit);
        assertThat(lines.getFirst().getPricedPeriods().getFirst().getPeriodAmount()).isEqualByComparingTo("9000");
    }

    @Test
    void lateConfiguredRuleCannotClearMandatoryHybridApprovalOrItsReason() {
        var result = governedWith("""
                rule "clear approval after hybrid rule"
                salience -10
                when $quote : QuoteFact()
                then
                    $quote.setString("approvalTier", null);
                    $quote.setString("hybridWindowReason", null);
                end
                """);
        assertThat(result.getApprovalState()).isEqualTo("PENDING");
        assertThat(result.getApprovalTier()).isEqualTo("DEAL_DESK");
        assertThat(result.getExplanationCodes()).contains("RAMP_HYBRID_WINDOW");
    }

    @Test
    void configuredCfoTierSurvivesTheMandatoryHybridMinimum() {
        var result = governedWith("""
                rule "configured CFO policy"
                salience -10
                when $quote : QuoteFact()
                then $quote.setString("approvalTier", "CFO"); end
                """);
        assertThat(result.getApprovalState()).isEqualTo("PENDING");
        assertThat(result.getApprovalTier()).isEqualTo("CFO");
        assertThat(result.getExplanationCodes()).contains("RAMP_HYBRID_WINDOW");
    }

    private PricingResult governedWith(String rule) {
        var context = context(12, 10, new BigDecimal("10000"));
        var lines = pricedLines();
        var plan = policy.prepare(context, products());
        policy.apply(plan, lines);
        var properties = new AcvVersionProperties();
        properties.setAcvVersion(AcvVersion.TERM_YEARS);
        var result = PricingResult.builder().lineResults(lines).approvalState("APPROVED")
                .explanationCodes(new ArrayList<>()).build();
        result.setFinancialMetrics(new MetricsCalculator(properties).calculate(result, 36));
        var configured = com.opencrm.rules.engine.RuleDefinition.builder().id("late-configured-policy")
                .content("package com.opencrm.pricing.rules;\nimport com.opencrm.rules.model.QuoteFact;\n" + rule).build();
        var engine = new com.opencrm.rules.drools.DroolsRulesEngine(new com.opencrm.rules.drools.DroolsSessionFactory(
                new com.opencrm.rules.drools.DroolsRuleCompiler()));
        new com.opencrm.pricing.ramp.PeriodGovernance().evaluate(context, result, engine, products(), List.of(configured), plan);
        return result;
    }

    private static PricingContext context(int overlapMonths, int seats, BigDecimal bookValue) {
        var declining = PricingLineRequest.builder().productExternalId("maintenance")
                .lineItemType("ONPREM_SUPPORT").quantity(seats).billingFrequency("MONTHLY")
                .metadata(Map.of("hybridMigration", Map.of("pairId", "pair-1", "role", "DECLINING",
                        "startMonth", 1, "months", overlapMonths, "bookValue", bookValue)))
                .schedule(List.of(step(1, 1, overlapMonths, seats, "100"),
                        step(2, overlapMonths + 1, 36 - overlapMonths, 0, "80"))).build();
        var rising = PricingLineRequest.builder().productExternalId("saas")
                .lineItemType("HYBRID_MIGRATION_RAMP").quantity(seats).billingFrequency("MONTHLY")
                .metadata(Map.of("hybridMigration", Map.of("pairId", "pair-1", "role", "RISING",
                        "startMonth", 1, "months", overlapMonths)))
                .schedule(List.of(step(1, 1, overlapMonths, seats, "50"),
                        step(2, overlapMonths + 1, 36 - overlapMonths, seats * 2, "60"))).build();
        return PricingContext.builder().customerExternalId("synthetic-customer").quoteExternalId("synthetic-quote")
                .durationInMonths(36).currency("USD").lineItems(List.of(declining, rising)).build();
    }

    private static RampStep step(int sequence, int start, int months, int quantity, String rate) {
        return RampStep.builder().sequence(sequence).startMonth(start).months(months).quantity(quantity)
                .priceBasis(RampPriceBasis.ABSOLUTE).unitPrice(new BigDecimal(rate)).build();
    }

    private static Map<String, PricingProduct> products() {
        return Map.of("maintenance", product("maintenance", ProductType.ONPREM_SUPPORT),
                "saas", product("saas", ProductType.HYBRID_MIGRATION_RAMP));
    }

    private static PricingProduct product(String id, ProductType type) {
        return PricingProduct.builder().externalId(id).productType(type).currency("USD")
                .listPrice(new BigDecimal("100")).build();
    }

    private static List<PricingLineResult> pricedLines() {
        return new ArrayList<>(List.of(
                PricingLineResult.builder().productExternalId("maintenance").quantity(10)
                        .listPrice(new BigDecimal("100")).metadata(Map.of())
                        .pricedPeriods(new ArrayList<>(List.of(period(1, 1, 12, 10, "100", "1000", "12000"),
                                period(2, 13, 24, 0, "80", "0", "0")))).build(),
                PricingLineResult.builder().productExternalId("saas").quantity(10)
                        .listPrice(new BigDecimal("100")).metadata(Map.of())
                        .pricedPeriods(new ArrayList<>(List.of(period(1, 1, 12, 10, "50", "500", "6000"),
                                period(2, 13, 24, 20, "60", "1200", "28800")))).build()));
    }

    private static PricedPeriod period(int sequence, int start, int months, int quantity,
                                      String unit, String monthly, String amount) {
        return PricedPeriod.builder().sequence(sequence).startMonth(start).months(months).quantity(quantity)
                .netUnitPrice(new BigDecimal(unit)).netMonthly(new BigDecimal(monthly))
                .periodAmount(new BigDecimal(amount)).discountRate(BigDecimal.ZERO)
                .explanationCodes(new ArrayList<>()).waterfall(new ArrayList<>()).build();
    }
}
