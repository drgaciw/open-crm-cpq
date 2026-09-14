package com.opencrm.pricing.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.rules.drools.DroolsRuleCompiler;
import com.opencrm.rules.model.CustomerFact;
import com.opencrm.rules.model.QuoteFact;
import com.opencrm.rules.model.QuoteLineItemFact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

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
