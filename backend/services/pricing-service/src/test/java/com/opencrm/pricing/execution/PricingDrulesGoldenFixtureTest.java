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
import com.opencrm.pricing.waterfall.PricingLineRequest;
import com.opencrm.pricing.waterfall.PricingResult;
import com.opencrm.rules.drools.DroolsRuleCompiler;
import com.opencrm.rules.drools.DroolsRulesEngine;
import com.opencrm.rules.drools.DroolsSessionFactory;
import com.opencrm.rules.model.CustomerFact;
import com.opencrm.rules.model.QuoteFact;
import com.opencrm.rules.model.QuoteLineItemFact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drools pack goldens for Phase 0 catalog rules and T1-3 metric-gated policy DRL.
 * Docker-free; compiles classpath DRL verbatim.
 */
@Tag("unit")
@DisplayName("Pricing DRL golden fixtures")
class PricingDrulesGoldenFixtureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private final DroolsRuleCompiler compiler = new DroolsRuleCompiler();

    @Test
    @DisplayName("volume named-seat 500 qty applies 12% discount")
    void volumeNamedSeat500() throws Exception {
        JsonNode fixture = readFixture("/fixtures/pricing/drl/volume-named-seat-500.json");
        String drl = readClasspathText(fixture.get("ruleClasspath").asText());
        KieBase kieBase = compiler.compileFromDrlString(drl);

        JsonNode facts = fixture.get("facts");
        QuoteFact quote = QuoteFact.builder()
                .durationInMonths(facts.get("durationInMonths").asInt())
                .build();
        QuoteLineItemFact line = QuoteLineItemFact.builder()
                .lineItemType(facts.get("lineItemType").asText())
                .quantity(facts.get("quantity").asInt())
                .listPrice(facts.get("listPrice").decimalValue())
                .quote(quote)
                .build();

        KieSession session = kieBase.newKieSession();
        try {
            session.insert(quote);
            session.insert(line);
            int fired = session.fireAllRules();

            JsonNode expected = fixture.get("expected");
            assertThat(fired).isEqualTo(expected.get("rulesFired").asInt());
            assertThat(line.getDiscountRate()).isEqualByComparingTo(expected.get("discountRate").asText());
            assertThat(line.getFinalPrice()).isEqualByComparingTo(expected.get("finalPrice").asText());
        } finally {
            session.dispose();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/fixtures/pricing/drl/approval-margin-below-floor.json",
            "/fixtures/pricing/drl/approval-margin-above-floor.json"
    })
    @DisplayName("approval-margin-exception.drl margin floor routing")
    void approvalMarginException(String fixturePath) throws Exception {
        JsonNode fixture = readFixture(fixturePath);
        String drl = readClasspathText(fixture.get("ruleClasspath").asText());
        KieBase kieBase = compiler.compileFromDrlString(drl);

        JsonNode facts = fixture.get("facts");
        QuoteFact quote = QuoteFact.builder()
                .basePrice(facts.get("basePrice").decimalValue())
                .finalPrice(facts.get("finalPrice").decimalValue())
                .build();
        quote.setDecimal("estimatedMarginPct", facts.get("estimatedMarginPct").decimalValue());

        KieSession session = kieBase.newKieSession();
        try {
            session.insert(quote);
            int fired = session.fireAllRules();

            JsonNode expected = fixture.get("expected");
            assertThat(fired).isEqualTo(expected.get("rulesFired").asInt());
            assertThat(quote.getString("approvalState")).isEqualTo(textOrNull(expected.get("approvalState")));
            assertThat(quote.getString("approvalTier")).isEqualTo(textOrNull(expected.get("approvalTier")));
            assertThat(quote.getString("explanationCode")).isEqualTo(textOrNull(expected.get("explanationCode")));
        } finally {
            session.dispose();
        }
    }

    @Test
    @DisplayName("TCV tiers apply 1.10 multiplier when TCV >= 500K")
    void tcvPricingTiers500k() throws Exception {
        JsonNode fixture = readFixture("/fixtures/pricing/drl/tcv-pricing-tiers-500k.json");
        QuoteFact quote = fireQuotePolicy(fixture);

        JsonNode expected = fixture.get("expected");
        assertThat(quote.getFinalPrice()).isEqualByComparingTo(expected.get("finalPrice").asText());
        assertThat(quote.getDecimal("priceMultiplier"))
                .isEqualByComparingTo(expected.get("priceMultiplier").asText());
        assertThat(quote.getString("explanationCode")).isEqualTo(expected.get("explanationCode").asText());
    }

    @Test
    @DisplayName("ACV minimum commitment floors finalPrice when ACV < 50K")
    void acvMinimumCommitmentFloor() throws Exception {
        JsonNode fixture = readFixture("/fixtures/pricing/drl/acv-minimum-commitment-floor.json");
        QuoteFact quote = fireQuotePolicy(fixture);

        JsonNode expected = fixture.get("expected");
        assertThat(quote.getFinalPrice()).isEqualByComparingTo(expected.get("finalPrice").asText());
        assertThat(quote.getDecimal("minCommitment"))
                .isEqualByComparingTo(expected.get("minCommitment").asText());
        assertThat(quote.getString("explanationCode")).isEqualTo(expected.get("explanationCode").asText());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/fixtures/pricing/drl/churn-risk-adjustment-high.json",
            "/fixtures/pricing/drl/churn-risk-adjustment-renewal-skip.json"
    })
    @DisplayName("churn-risk-adjustment.drl non-renewal only")
    void churnRiskAdjustment(String fixturePath) throws Exception {
        JsonNode fixture = readFixture(fixturePath);
        QuoteFact quote = fireQuotePolicy(fixture);

        JsonNode expected = fixture.get("expected");
        assertThat(quote.getFinalPrice()).isEqualByComparingTo(expected.get("finalPrice").asText());
        assertThat(quote.getString("explanationCode")).isEqualTo(textOrNull(expected.get("explanationCode")));
        if (expected.get("churnAdjustment") == null || expected.get("churnAdjustment").isNull()) {
            assertThat(quote.getDecimal("churnAdjustment")).isNull();
        } else {
            assertThat(quote.getDecimal("churnAdjustment"))
                    .isEqualByComparingTo(expected.get("churnAdjustment").asText());
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/fixtures/pricing/drl/renewal-uplift-standard-8pct.json",
            "/fixtures/pricing/drl/renewal-uplift-high-churn-3pct.json",
            "/fixtures/pricing/drl/renewal-uplift-caller-supplied.json"
    })
    @DisplayName("renewal-uplift.drl golden outcomes do not change finalPrice")
    void renewalUpliftGolden(String fixturePath) throws Exception {
        JsonNode fixture = readFixture(fixturePath);
        QuoteFact quote = fireQuotePolicy(fixture);
        JsonNode expected = fixture.get("expected");
        assertThat(quote.getFinalPrice()).isEqualByComparingTo(expected.get("finalPrice").asText());
        if (expected.get("renewalUpliftPct") == null || expected.get("renewalUpliftPct").isNull()) {
            assertThat(quote.getDecimal("renewalUpliftPct")).isNull();
        } else {
            assertThat(quote.getDecimal("renewalUpliftPct")).isEqualByComparingTo(expected.get("renewalUpliftPct").asText());
        }
        assertThat(quote.getString("retentionOffer")).isEqualTo(textOrNull(expected.get("retentionOffer")));
        assertThat(quote.getString("explanationCode")).isEqualTo(textOrNull(expected.get("explanationCode")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"NEW", "AMENDMENT", "EXPANSION", "NEW_BUSINESS", "EXISTING_BUSINESS",
            "UPSELL", "CROSS_SELL", "renewal", "EXTENSION", " RENEWAL", ""})
    @DisplayName("renewal uplift ignores every deal type except exact RENEWAL")
    void renewalUpliftIgnoresOtherDealTypes(String dealType) throws Exception {
        QuoteFact quote = renewalQuote(dealType, 80);
        assertThat(fire(quote)).isZero();
        assertThat(quote.getDecimal("renewalUpliftPct")).isNull();
        assertThat(quote.getString("retentionOffer")).isNull();
    }

    @Test
    @DisplayName("renewal uplift does not fire without a customer")
    void renewalUpliftRequiresCustomer() throws Exception {
        QuoteFact quote = QuoteFact.builder().finalPrice(new BigDecimal("1000")).build();
        quote.setString("dealType", "RENEWAL");
        assertThat(fire(quote)).isZero();
        assertThat(quote.getDecimal("renewalUpliftPct")).isNull();
    }

    @Test
    @DisplayName("renewal uplift does not replace an explanation another rule already wrote")
    void renewalUpliftDoesNotClobberExplanation() throws Exception {
        QuoteFact quote = renewalQuote("RENEWAL", 80);
        quote.setString("explanationCode", "KEEP");
        assertThat(fire(quote)).isEqualTo(1);
        assertThat(quote.getDecimal("renewalUpliftPct")).isEqualByComparingTo("0.03");
        assertThat(quote.getString("explanationCode")).isEqualTo("KEEP");
    }

    @Test
    @DisplayName("renewal uplift salience is below ramp governance and every other catalog rule")
    void renewalUpliftIsLowestSalience() throws Exception {
        int renewal = salience("rules/renewal-uplift.drl");
        assertThat(renewal).isLessThan(80);
        for (String rule : List.of(
                "rules/acv-minimum-commitment.drl",
                "rules/amendment-proration.drl",
                "rules/approval-margin-exception.drl",
                "rules/churn-risk-adjustment.drl",
                "rules/core-license-pack.drl",
                "rules/migration-credit.drl",
                "rules/perpetual-maintenance.drl",
                "rules/tcv-pricing-tiers.drl",
                "rules/usage-overage-charge.drl",
                "rules/volume-discount-named-seat.drl")) {
            assertThat(salience(rule)).isGreaterThan(renewal);
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {80})
    @DisplayName("execute records the renewal default without changing the price or writing a decision audit")
    void executeRenewalUpliftDoesNotChangePrice(Integer storedChurn) throws Exception {
        PricingAuditLogRepository auditLogRepository = mock(PricingAuditLogRepository.class);
        when(auditLogRepository.save(any(PricingAuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CustomerRepository customers = mock(CustomerRepository.class);
        when(customers.findByExternalIdAndDeletedFalse("CUST-1")).thenReturn(Optional.of(Customer.builder()
                .externalId("CUST-1").name("Acme").region(CustomerRegion.NA).churnScore(storedChurn).build()));
        PricingProductRepository products = mock(PricingProductRepository.class);
        when(products.findByExternalIdInAndDeletedFalse(any())).thenReturn(List.of());
        RuleSetRepository ruleSets = mock(RuleSetRepository.class);
        when(ruleSets.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any())).thenReturn(List.of());
        PriceBookResolutionClient priceBooks = mock(PriceBookResolutionClient.class);
        when(priceBooks.resolveBatch(any(), any())).thenReturn(ResolveBatchResponse.builder()
                .entries(Map.of("PROD-1", ProductEntryResolveDto.builder().entryMatched(true)
                        .listPrice(new BigDecimal("100.00")).currency("USD").build()))
                .build());
        PricingExecutionService service = new PricingExecutionService(
                customers, products, ruleSets, mock(RuleRepository.class), priceBooks, auditLogRepository,
                new PricingContextValidator(), new MetricsCalculator(), MAPPER,
                new DroolsRulesEngine(new DroolsSessionFactory(compiler)));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("dealType", "RENEWAL");
        metadata.put("churnScore", storedChurn == null ? 80 : 10);
        PricingResult result = service.execute(PricingContext.builder()
                .customerExternalId("CUST-1").quoteExternalId("Q-1").region("NA").currency("USD")
                .durationInMonths(12).metadata(metadata)
                .lineItems(List.of(PricingLineRequest.builder().productExternalId("PROD-1")
                        .lineItemType("SUBSCRIPTION").quantity(2)
                        .metadata(Map.of("renewalUpliftSource", "RENEWAL_UPLIFT_POLICY", "retentionOffer", "FORGED")).build()))
                .build());

        assertThat(result.getLineResults().getFirst().getFinalPrice()).isEqualByComparingTo("200.00");
        assertThat(result.getLineResults().getFirst().getUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(result.getLineResults().getFirst().getMetadata().get("renewalUpliftPct")).isEqualTo(storedChurn == null ? "0.08" : "0.03");
        assertThat(result.getLineResults().getFirst().getMetadata().get("renewalUpliftSource")).isEqualTo("RENEWAL_UPLIFT_POLICY");
        if (storedChurn == null) {
            assertThat(result.getLineResults().getFirst().getMetadata()).doesNotContainKey("retentionOffer");
            assertThat(result.getWaterfall().getExplanationCodes()).doesNotContain("ENABLE_12M_PRICE_PROTECTION");
        } else {
            assertThat(result.getLineResults().getFirst().getMetadata().get("retentionOffer"))
                    .isEqualTo("ENABLE_12M_PRICE_PROTECTION");
            assertThat(result.getWaterfall().getExplanationCodes()).contains("ENABLE_12M_PRICE_PROTECTION");
        }
        assertThat(result.getWaterfall().getNetPrice()).isEqualByComparingTo("200.00");
        assertThat(result.getWaterfall().getExplanationCodes()).contains("RENEWAL_UPLIFT_POLICY");
        assertThat(result.getExplanationCodes()).contains("RENEWAL_UPLIFT_POLICY");

        ArgumentCaptor<PricingAuditLog> saved = ArgumentCaptor.forClass(PricingAuditLog.class);
        verify(auditLogRepository).save(saved.capture());
        assertThat(saved.getValue().getRulesFired()).isNull();
        assertThat(saved.getValue().getWaterfallTrace()).isNull();
        assertThat(saved.getValue().getOutputSnapshot()).doesNotContain("FORGED");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"NEW", "AMENDMENT", "EXPANSION", "NEW_BUSINESS", "renewal"})
    @DisplayName("execute does not run renewal uplift for other deal types")
    void executeIgnoresNonRenewalDealTypes(String dealType) {
        PricingAuditLogRepository auditLogRepository = mock(PricingAuditLogRepository.class);
        when(auditLogRepository.save(any(PricingAuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CustomerRepository customers = mock(CustomerRepository.class);
        when(customers.findByExternalIdAndDeletedFalse("CUST-1")).thenReturn(Optional.of(Customer.builder()
                .externalId("CUST-1").name("Acme").region(CustomerRegion.NA).churnScore(80).build()));
        PricingProductRepository products = mock(PricingProductRepository.class);
        when(products.findByExternalIdInAndDeletedFalse(any())).thenReturn(List.of());
        RuleSetRepository ruleSets = mock(RuleSetRepository.class);
        when(ruleSets.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any())).thenReturn(List.of());
        PriceBookResolutionClient priceBooks = mock(PriceBookResolutionClient.class);
        when(priceBooks.resolveBatch(any(), any())).thenReturn(ResolveBatchResponse.builder()
                .entries(Map.of("PROD-1", ProductEntryResolveDto.builder().entryMatched(true)
                        .listPrice(new BigDecimal("100.00")).currency("USD").build()))
                .build());
        PricingExecutionService service = new PricingExecutionService(
                customers, products, ruleSets, mock(RuleRepository.class), priceBooks, auditLogRepository,
                new PricingContextValidator(), new MetricsCalculator(), MAPPER,
                new DroolsRulesEngine(new DroolsSessionFactory(compiler)));
        Map<String, Object> metadata = dealType == null ? null : Map.of("dealType", dealType, "churnScore", 10);
        PricingResult result = service.execute(PricingContext.builder()
                .customerExternalId("CUST-1").quoteExternalId("Q-1").region("NA").currency("USD")
                .durationInMonths(12).metadata(metadata)
                .lineItems(List.of(PricingLineRequest.builder().productExternalId("PROD-1")
                        .lineItemType("SUBSCRIPTION").quantity(2)
                        .metadata(Map.of("renewalUpliftSource", "RENEWAL_UPLIFT_POLICY", "retentionOffer", "FORGED")).build()))
                .build());
        assertThat(result.getLineResults().getFirst().getFinalPrice()).isEqualByComparingTo("200.00");
        assertThat(result.getLineResults().getFirst().getMetadata()).doesNotContainKeys("renewalUpliftSource", "retentionOffer");
        assertThat(result.getWaterfall().getExplanationCodes()).doesNotContain("RENEWAL_UPLIFT_POLICY");
        ArgumentCaptor<PricingAuditLog> saved = ArgumentCaptor.forClass(PricingAuditLog.class);
        verify(auditLogRepository).save(saved.capture());
        assertThat(saved.getValue().getRulesFired()).isNull();
        assertThat(saved.getValue().getWaterfallTrace()).isNull();
        assertThat(saved.getValue().getOutputSnapshot()).doesNotContain("FORGED");
    }

    private int fire(QuoteFact quote) throws IOException {
        KieBase kieBase = compiler.compileFromDrlString(readClasspathText("rules/renewal-uplift.drl"));
        KieSession session = kieBase.newKieSession();
        try {
            session.insert(quote);
            return session.fireAllRules();
        } finally {
            session.dispose();
        }
    }

    private static QuoteFact renewalQuote(String dealType, int churnScore) {
        QuoteFact quote = QuoteFact.builder()
                .finalPrice(new BigDecimal("1000"))
                .customer(CustomerFact.builder().churnScore(churnScore).build())
                .build();
        if (dealType != null) quote.setString("dealType", dealType);
        return quote;
    }

    private static int salience(String classpath) throws IOException {
        Matcher matcher = Pattern.compile("salience\\s+(\\d+)").matcher(readClasspathText(classpath));
        assertThat(matcher.find()).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    private QuoteFact fireQuotePolicy(JsonNode fixture) throws IOException {
        String drl = readClasspathText(fixture.get("ruleClasspath").asText());
        KieBase kieBase = compiler.compileFromDrlString(drl);

        JsonNode facts = fixture.get("facts");
        QuoteFact.QuoteFactBuilder builder = QuoteFact.builder();
        if (facts.hasNonNull("basePrice")) {
            builder.basePrice(facts.get("basePrice").decimalValue());
        }
        if (facts.hasNonNull("finalPrice")) {
            builder.finalPrice(facts.get("finalPrice").decimalValue());
        }
        if (facts.hasNonNull("churnScore")) {
            builder.customer(CustomerFact.builder()
                    .churnScore(facts.get("churnScore").asInt())
                    .build());
        }
        QuoteFact quote = builder.build();
        if (facts.hasNonNull("tcv")) {
            quote.setDecimal("tcv", facts.get("tcv").decimalValue());
        }
        if (facts.hasNonNull("acv")) {
            quote.setDecimal("acv", facts.get("acv").decimalValue());
        }
        if (facts.hasNonNull("dealType")) {
            quote.setString("dealType", facts.get("dealType").asText());
        }
        if (facts.hasNonNull("renewalUpliftPct")) {
            quote.setDecimal("renewalUpliftPct", facts.get("renewalUpliftPct").decimalValue());
        }
        if (facts.has("renewalUpliftSupplied") && !facts.get("renewalUpliftSupplied").isNull()) {
            quote.setBoolean("renewalUpliftSupplied", facts.get("renewalUpliftSupplied").asBoolean());
        }

        KieSession session = kieBase.newKieSession();
        try {
            session.insert(quote);
            int fired = session.fireAllRules();
            assertThat(fired).isEqualTo(fixture.get("expected").get("rulesFired").asInt());
            return quote;
        } finally {
            session.dispose();
        }
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static JsonNode readFixture(String classpath) throws IOException {
        try (InputStream in = PricingDrulesGoldenFixtureTest.class.getResourceAsStream(classpath)) {
            assertThat(in).as("fixture %s", classpath).isNotNull();
            return MAPPER.readTree(in);
        }
    }

    private static String readClasspathText(String classpath) throws IOException {
        String path = classpath.startsWith("/") ? classpath : "/" + classpath;
        try (InputStream in = PricingDrulesGoldenFixtureTest.class.getResourceAsStream(path)) {
            assertThat(in).as("rule %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
