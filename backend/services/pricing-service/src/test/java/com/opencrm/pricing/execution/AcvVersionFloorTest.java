package com.opencrm.pricing.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import com.opencrm.rules.drools.DroolsRuleCompiler;
import com.opencrm.rules.model.QuoteFact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-023 D-03 floors. The ACV each formula produces is inserted on a {@link QuoteFact}.
 * Execute does not insert that fact; this test does not call {@code PricingExecutionService}.
 */
@Tag("unit")
@DisplayName("ADR-023 ACV floors")
class AcvVersionFloorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String RULE = "/rules/acv-minimum-commitment.drl";
    private static final BigDecimal PRICE_BELOW_BOTH_FLOORS = new BigDecimal("1000.00");

    private final DroolsRuleCompiler compiler = new DroolsRuleCompiler();
    private final MetricsCalculator legacy = new MetricsCalculator(AcvVersion.LEGACY);
    private final MetricsCalculator termYears = new MetricsCalculator(AcvVersion.TERM_YEARS);

    @Test
    @DisplayName("monthly 5,000 for 12 months: legacy floor 50,000 and term-years floor 100,000")
    void fiveThousandMonthlyFloorsBothWays() throws Exception {
        FinancialMetrics legacyMetrics = legacy.calculate(monthly("5000"), 12);
        FinancialMetrics termYearsMetrics = termYears.calculate(monthly("5000"), 12);
        assertThat(legacyMetrics.getAcv()).isEqualByComparingTo("5000.00");
        assertThat(termYearsMetrics.getAcv()).isEqualByComparingTo("60000.00");

        QuoteFact legacyQuote = fire(legacyMetrics.getAcv());
        assertThat(legacyQuote.getFinalPrice()).isEqualByComparingTo("50000");
        assertThat(legacyQuote.getDecimal("minCommitment")).isEqualByComparingTo("50000");
        assertThat(legacyQuote.getString("explanationCode")).isEqualTo("ACV_MIN_COMMITMENT");

        QuoteFact termYearsQuote = fire(termYearsMetrics.getAcv());
        assertThat(termYearsQuote.getFinalPrice()).isEqualByComparingTo("100000");
        assertThat(termYearsQuote.getDecimal("minCommitment")).isEqualByComparingTo("100000");
        assertThat(termYearsQuote.getString("explanationCode")).isEqualTo("ACV_MIN_COMMITMENT");
    }

    @Test
    @DisplayName("monthly 10,000 for 12 months: legacy floor 50,000 and term-years has no floor")
    void tenThousandMonthlyFloorsOnlyOnLegacy() throws Exception {
        FinancialMetrics legacyMetrics = legacy.calculate(monthly("10000"), 12);
        FinancialMetrics termYearsMetrics = termYears.calculate(monthly("10000"), 12);
        assertThat(legacyMetrics.getAcv()).isEqualByComparingTo("10000.00");
        assertThat(termYearsMetrics.getAcv()).isEqualByComparingTo("120000.00");

        QuoteFact legacyQuote = fire(legacyMetrics.getAcv());
        assertThat(legacyQuote.getFinalPrice()).isEqualByComparingTo("50000");
        assertThat(legacyQuote.getDecimal("minCommitment")).isEqualByComparingTo("50000");
        assertThat(legacyQuote.getString("explanationCode")).isEqualTo("ACV_MIN_COMMITMENT");

        QuoteFact termYearsQuote = fire(termYearsMetrics.getAcv());
        assertThat(termYearsQuote.getFinalPrice()).isEqualByComparingTo(PRICE_BELOW_BOTH_FLOORS);
        assertThat(termYearsQuote.getDecimal("minCommitment")).isNull();
        assertThat(termYearsQuote.getString("explanationCode")).isNull();
    }

    @Test
    @DisplayName("term-years fixture differs from execute/01 in acv only and names ADR-023")
    void termYearsFixtureDiffersInAcvOnly() throws Exception {
        String raw = readText("/fixtures/pricing/acv-term-years/01-list-price-baseline.json");
        assertThat(raw).contains("ADR-023");

        JsonNode baseline = MAPPER.readTree(readText("/fixtures/pricing/execute/01-list-price-baseline.json"));
        JsonNode termYearsFixture = MAPPER.readTree(raw);
        assertSameExceptAcvAndAdrCitation(baseline, termYearsFixture, "");

        FinancialMetrics metrics = termYears.calculate(monthly("500"), 12);
        assertThat(metrics.getAcv()).isEqualByComparingTo("6000.00");
        assertThat(metrics.getTcv()).isEqualByComparingTo(termYearsFixture.at("/expected/financialMetrics/tcv").decimalValue());
        assertThat(metrics.getArr()).isEqualByComparingTo(termYearsFixture.at("/expected/financialMetrics/arr").decimalValue());
        assertThat(termYearsFixture.at("/expected/financialMetrics/acv").decimalValue())
                .isEqualByComparingTo(metrics.getAcv());
    }

    private QuoteFact fire(BigDecimal acv) throws IOException {
        KieBase kieBase = compiler.compileFromDrlString(readText(RULE));
        QuoteFact quote = QuoteFact.builder()
                .finalPrice(PRICE_BELOW_BOTH_FLOORS)
                .durationInMonths(12)
                .build();
        quote.setDecimal("acv", acv);

        KieSession session = kieBase.newKieSession();
        try {
            session.insert(quote);
            assertThat(session.fireAllRules()).isEqualTo(1);
            return quote;
        } finally {
            session.dispose();
        }
    }

    private static PricingResult monthly(String monthlyFinalPrice) {
        return PricingResult.builder()
                .lineResults(List.of(PricingLineResult.builder()
                        .quantity(1)
                        .listPrice(new BigDecimal(monthlyFinalPrice))
                        .finalPrice(new BigDecimal(monthlyFinalPrice))
                        .discountAmount(BigDecimal.ZERO)
                        .surchargeAmount(BigDecimal.ZERO)
                        .build()))
                .build();
    }

    private static void assertSameExceptAcvAndAdrCitation(JsonNode baseline, JsonNode candidate, String pointer) {
        assertThat(candidate.getNodeType()).as(pointer).isEqualTo(baseline.getNodeType());
        if (baseline.isObject()) {
            assertThat(fieldNames(candidate)).as(pointer).containsExactlyInAnyOrderElementsOf(fieldNames(baseline));
            Iterator<String> names = baseline.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                assertSameExceptAcvAndAdrCitation(baseline.get(name), candidate.get(name), pointer + "/" + name);
            }
            return;
        }
        if (baseline.isArray()) {
            assertThat(candidate.size()).as(pointer).isEqualTo(baseline.size());
            for (int i = 0; i < baseline.size(); i++) {
                assertSameExceptAcvAndAdrCitation(baseline.get(i), candidate.get(i), pointer + "/" + i);
            }
            return;
        }
        if ("/expected/financialMetrics/acv".equals(pointer)) {
            assertThat(baseline.decimalValue()).isEqualByComparingTo("500.00");
            assertThat(candidate.decimalValue()).isEqualByComparingTo("6000.00");
            return;
        }
        if ("/description".equals(pointer)) {
            assertThat(candidate.asText()).isEqualTo(baseline.asText() + " ADR-023.");
            return;
        }
        assertThat(candidate).as(pointer).isEqualTo(baseline);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static String readText(String classpath) throws IOException {
        try (InputStream in = AcvVersionFloorTest.class.getResourceAsStream(classpath)) {
            assertThat(in).as(classpath).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
