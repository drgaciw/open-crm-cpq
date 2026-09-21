package com.opencrm.jev.shadow;

import com.opencrm.jev.JevClient;
import com.opencrm.jev.JevProperties;
import com.opencrm.jev.JevResult;
import com.opencrm.jev.answer.JevAnswer;
import com.opencrm.jev.question.JevQuestion;
import com.opencrm.jev.question.JevQuestions;
import com.opencrm.jev.state.FeatureNotAllowedException;
import com.opencrm.jev.state.JevFeatureSchema;
import com.opencrm.jev.testing.RecordedJevTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Offline shadow evaluation (ADR-015 stage 1)")
class ShadowAgreementReportTest {

    static final JevFeatureSchema DISCOUNT_TIER = JevFeatureSchema.named("discount-tier", 1)
            .number("margin_delta").number("requested_discount").code("currency_code").build();

    static final JevFeatureSchema QUOTE_TIER = JevFeatureSchema.named("quote-tier", 1)
            .code("total_amount_band").number("discount_pct").integer("duration_months").code("deal_type")
            .integer("line_count").code("currency").code("region").build();

    static final JevFeatureSchema LEAD_SCORE = JevFeatureSchema.named("lead-score", 1)
            .code("source").code("priority").code("status").code("estimated_value_band").code("currency")
            .bool("has_contact").bool("has_organization").integer("days_since_created")
            .integer("days_since_last_contacted").bool("has_next_action_date").build();

    static final JevFeatureSchema DUPLICATE_PAIR = JevFeatureSchema.named("duplicate-pair", 1)
            .number("trigram_similarity").number("token_jaccard").integer("shared_token_count")
            .bool("suffix_stripped_equal").bool("same_domain").bool("same_country").bool("same_city")
            .integer("length_delta").build();

    private static Map<String, String> keys(String... keys) {
        Map<String, String> criteria = new LinkedHashMap<>();
        for (String key : keys) {
            criteria.put(key, key);
        }
        return criteria;
    }

    private static RecordedJevTransport fixture(JevFeatureSchema schema, String folder) {
        return new RecordedJevTransport(schema,
                ShadowAgreementReportTest.class.getResourceAsStream("/fixtures/" + folder + "/cases.json"));
    }

    private static JevClient client(RecordedJevTransport transport) {
        JevProperties properties = new JevProperties();
        properties.setEnabled(true);
        properties.setModel("recorded");
        properties.getLeadScore().setEnabled(true);
        properties.getLeadScore().setMinConfidence(0);
        properties.getDiscountTierShadow().setEnabled(true);
        properties.getQuoteTierSuggestion().setEnabled(true);
        properties.getQuoteTierSuggestion().setMinConfidence(0);
        properties.getDuplicateHints().setEnabled(true);
        return new JevClient(properties, transport);
    }

    private static ShadowAgreementReport replayChoice(RecordedJevTransport transport, String useCase,
                                                      JevQuestion.Choice question) {
        JevClient client = client(transport);
        List<ShadowAgreementReport.Observation> observations = new ArrayList<>();
        for (RecordedJevTransport.Case fixtureCase : transport.cases()) {
            JevResult result = client.decide(useCase, fixtureCase.state(), List.of(question));
            assertThat(result.status()).as(fixtureCase.name()).isEqualTo(JevResult.Status.OK);
            JevAnswer.Choice answer = result.answer(question.id(), JevAnswer.Choice.class).orElseThrow();
            observations.add(new ShadowAgreementReport.Observation(
                    fixtureCase.authoritative(), answer.choice(), answer.confidence()));
        }
        return ShadowAgreementReport.of(observations);
    }

    @Test
    @DisplayName("agreement rate, confusion matrix and calibration error are computed")
    void computesReport() {
        ShadowAgreementReport report = ShadowAgreementReport.of(List.of(
                new ShadowAgreementReport.Observation("A", "A", 0.95),
                new ShadowAgreementReport.Observation("A", "A", 0.95),
                new ShadowAgreementReport.Observation("A", "B", 0.95),
                new ShadowAgreementReport.Observation("B", "B", 0.55)));

        assertThat(report.compared()).isEqualTo(4);
        assertThat(report.agreementRate()).isEqualTo(0.75);
        assertThat(report.confusion()).containsEntry("A", Map.of("A", 2, "B", 1)).containsEntry("B", Map.of("B", 1));
        // bin 0.9-1.0: accuracy 2/3 vs confidence 0.95 (weight 3/4); bin 0.5-0.6: 1.0 vs 0.55 (weight 1/4)
        assertThat(report.expectedCalibrationError())
                .isCloseTo(0.75 * Math.abs(2.0 / 3 - 0.95) + 0.25 * 0.45, within(1e-9));
    }

    @Test
    @DisplayName("an empty comparison reports zeros rather than dividing by zero")
    void emptyReport() {
        assertThat(ShadowAgreementReport.of(List.of()).compared()).isZero();
    }

    @Test
    @DisplayName("discount-tier fixtures replay end to end; disagreement sits at the rule boundaries")
    void replaysDiscountTier() {
        RecordedJevTransport transport = fixture(DISCOUNT_TIER, "discount-tier");
        ShadowAgreementReport report = replayChoice(transport, JevProperties.DISCOUNT_TIER_SHADOW,
                JevQuestions.choice("tier", "Which approval tier?", keys("MANAGER", "DIRECTOR", "VP_SALES")));

        assertThat(transport.synthetic()).isTrue();
        assertThat(report.compared()).isEqualTo(30);
        assertThat(report.agreementRate()).isCloseTo(26.0 / 30, within(1e-9));
        assertThat(report.confusion().get("MANAGER")).containsEntry("DIRECTOR", 1);
    }

    @Test
    @DisplayName("quote-tier fixtures replay end to end")
    void replaysQuoteTier() {
        ShadowAgreementReport report = replayChoice(fixture(QUOTE_TIER, "quote-tier"),
                JevProperties.QUOTE_TIER_SUGGESTION,
                JevQuestions.choice("tier", "Which approval tier?", keys("DEAL_DESK", "SALES_VP", "CFO")));

        assertThat(report.compared()).isEqualTo(30);
        assertThat(report.agreementRate()).isBetween(0.8, 0.95);
    }

    @Test
    @DisplayName("lead-score and duplicate-pair fixtures pass the allow-list and validate")
    void replaysUnsupervisedUseCases() {
        RecordedJevTransport leads = fixture(LEAD_SCORE, "lead-score");
        JevQuestion qualify = JevQuestions.score("qualify", "How likely is this lead to qualify?",
                List.of("very unlikely", "unlikely", "uncertain", "likely", "very likely"));
        for (RecordedJevTransport.Case fixtureCase : leads.cases()) {
            JevResult result = client(leads).decide(JevProperties.LEAD_SCORE, fixtureCase.state(), List.of(qualify));
            assertThat(result.status()).as(fixtureCase.name()).isEqualTo(JevResult.Status.OK);
            assertThat(fixtureCase.authoritative()).isNull();
        }

        RecordedJevTransport pairs = fixture(DUPLICATE_PAIR, "duplicate-pair");
        JevQuestion same = JevQuestions.noul("same", "Do these two records describe the same organization?");
        for (RecordedJevTransport.Case fixtureCase : pairs.cases()) {
            JevResult result = client(pairs).decide(JevProperties.DUPLICATE_HINTS, fixtureCase.state(), List.of(same));
            assertThat(result.status()).as(fixtureCase.name()).isEqualTo(JevResult.Status.OK);
        }
        assertThat(leads.cases()).hasSize(30);
        assertThat(pairs.cases()).hasSize(30);
    }

    @Test
    @DisplayName("a fixture carrying a field outside the allow-list cannot be loaded")
    void fixtureCannotSmuggleFields() {
        String fixture = """
                {"cases":[{"name":"leak","features":{"margin_delta":25.0,"customer_name":"Acme"},
                           "authoritative":"DIRECTOR","answers":{}}]}""";

        assertThatThrownBy(() -> new RecordedJevTransport(DISCOUNT_TIER,
                new ByteArrayInputStream(fixture.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(FeatureNotAllowedException.class);
    }
}
