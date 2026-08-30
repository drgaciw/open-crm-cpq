package com.opencrm.pricing.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.rules.drools.DroolsRuleCompiler;
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
 * S2C-1b Drools pack goldens for volume discount and approval-margin-exception signals.
 * Docker-free; compiles classpath DRL verbatim. Matrix §4.
 */
@Tag("unit")
@DisplayName("Pricing DRL golden fixtures (S2C-1b)")
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
