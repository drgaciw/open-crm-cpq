package com.opencrm.jev;

import com.opencrm.jev.answer.JevAnswer;
import com.opencrm.jev.error.JevException;
import com.opencrm.jev.question.JevQuestion;
import com.opencrm.jev.question.JevQuestions;
import com.opencrm.jev.state.JevFeatureSchema;
import com.opencrm.jev.state.JevState;
import com.opencrm.jev.testing.FakeJevTransport;
import com.opencrm.jev.transport.NoOpJevTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JevClient never throws")
class JevClientNeverThrowsTest {

    private static final String USE_CASE = JevProperties.DISCOUNT_TIER_SHADOW;
    private static final JevFeatureSchema SCHEMA =
            JevFeatureSchema.named("discount-tier", 1).number("margin_delta").build();
    private static final JevState STATE = JevState.builder(SCHEMA).put("margin_delta", 25.0).build();
    private static final List<JevQuestion> QUESTIONS = List.of(JevQuestions.choice("tier", "Which tier?", tiers()));

    private static final String DIRECTOR = """
            {"tier":{"type":"choice","choice":"DIRECTOR","confidence":0.8,
                     "probabilities":{"MANAGER":0.1,"DIRECTOR":0.8,"VP_SALES":0.1}}}""";

    private static Map<String, String> tiers() {
        Map<String, String> tiers = new LinkedHashMap<>();
        tiers.put("MANAGER", "small");
        tiers.put("DIRECTOR", "medium");
        tiers.put("VP_SALES", "large");
        return tiers;
    }

    private static JevProperties enabled() {
        JevProperties properties = new JevProperties();
        properties.setEnabled(true);
        properties.setModel("typesafe/jev-1.13");
        properties.getDiscountTierShadow().setEnabled(true);
        return properties;
    }

    @Test
    @DisplayName("default properties are DISABLED and send nothing")
    void defaultsAreDisabled() {
        FakeJevTransport transport = new FakeJevTransport();

        JevResult result = new JevClient(new JevProperties(), transport).decide(USE_CASE, STATE, QUESTIONS);

        assertThat(result.status()).isEqualTo(JevResult.Status.DISABLED);
        assertThat(transport.requests()).isEmpty();
    }

    @Test
    @DisplayName("the use case flag alone gates the call")
    void useCaseFlagGates() {
        JevProperties properties = enabled();
        properties.getDiscountTierShadow().setEnabled(false);
        FakeJevTransport transport = new FakeJevTransport();

        JevResult result = new JevClient(properties, transport).decide(USE_CASE, STATE, QUESTIONS);

        assertThat(result.status()).isEqualTo(JevResult.Status.DISABLED);
        assertThat(transport.requests()).isEmpty();
    }

    @Test
    @DisplayName("the no-op transport is DISABLED even when every flag is on")
    void noOpTransportIsDisabled() {
        JevResult result = new JevClient(enabled(), new NoOpJevTransport()).decide(USE_CASE, STATE, QUESTIONS);

        assertThat(result.status()).isEqualTo(JevResult.Status.DISABLED);
        assertThat(result.transport()).isEqualTo("noop");
    }

    @Test
    @DisplayName("an unknown use case is DISABLED")
    void unknownUseCase() {
        JevResult result = new JevClient(enabled(), new FakeJevTransport()).decide("deal-probability", STATE, QUESTIONS);

        assertThat(result.status()).isEqualTo(JevResult.Status.DISABLED);
    }

    @Test
    @DisplayName("a valid answer is OK and carries the pinned evaluator config")
    void validAnswer() {
        JevResult result = new JevClient(enabled(), new FakeJevTransport().reply(DIRECTOR))
                .decide(USE_CASE, STATE, QUESTIONS);

        assertThat(result.usable()).isTrue();
        assertThat(result.answer("tier", JevAnswer.Choice.class)).hasValueSatisfying(
                choice -> assertThat(choice.choice()).isEqualTo("DIRECTOR"));
        assertThat(result.confidence()).isEqualTo(0.8);
        assertThat(result.model()).isEqualTo("typesafe/jev-1.13");
        assertThat(result.rubricVersion()).isEqualTo("tier-v1-thresholds");
    }

    @Test
    @DisplayName("confidence below the use case minimum ABSTAINS but keeps the answer for the log")
    void abstainsBelowMinimum() {
        JevProperties properties = enabled();
        properties.getDiscountTierShadow().setMinConfidence(0.9);

        JevResult result = new JevClient(properties, new FakeJevTransport().reply(DIRECTOR))
                .decide(USE_CASE, STATE, QUESTIONS);

        assertThat(result.status()).isEqualTo(JevResult.Status.ABSTAINED);
        assertThat(result.usable()).isFalse();
        assertThat(result.answers()).containsKey("tier");
    }

    @Test
    @DisplayName("a contract-breaking answer is INVALID")
    void invalidAnswer() {
        JevResult result = new JevClient(enabled(), new FakeJevTransport().reply("""
                {"tier":{"choice":"CFO","confidence":0.9,
                         "probabilities":{"MANAGER":0.0,"DIRECTOR":0.1,"VP_SALES":0.9}}}"""))
                .decide(USE_CASE, STATE, QUESTIONS);

        assertThat(result.status()).isEqualTo(JevResult.Status.INVALID);
        assertThat(result.answers()).isEmpty();
    }

    @Test
    @DisplayName("a timeout is TIMEOUT, other transport failures are ERROR")
    void transportFailures() {
        JevResult timeout = new JevClient(enabled(),
                new FakeJevTransport().fail(new JevException.Timeout("slow", null)))
                .decide(USE_CASE, STATE, QUESTIONS);
        JevResult error = new JevClient(enabled(),
                new FakeJevTransport().fail(new JevException.Authentication("no", 401)))
                .decide(USE_CASE, STATE, QUESTIONS);

        assertThat(timeout.status()).isEqualTo(JevResult.Status.TIMEOUT);
        assertThat(error.status()).isEqualTo(JevResult.Status.ERROR);
        assertThat(error.errorClass()).isEqualTo("Authentication");
    }

    @Test
    @DisplayName("an unexpected runtime failure is swallowed as ERROR")
    void unexpectedFailure() {
        JevResult result = new JevClient(enabled(),
                new FakeJevTransport().fail(new NullPointerException("bug")))
                .decide(USE_CASE, STATE, QUESTIONS);

        assertThat(result.status()).isEqualTo(JevResult.Status.ERROR);
    }

    @Test
    @DisplayName("the daily budget stops further calls")
    void budgetExhausted() {
        JevProperties properties = enabled();
        properties.setDailyCallBudget(1);
        FakeJevTransport transport = new FakeJevTransport().reply(DIRECTOR);
        JevClient client = new JevClient(properties, transport);

        JevResult first = client.decide(USE_CASE, STATE, QUESTIONS);
        JevResult second = client.decide(USE_CASE, STATE, QUESTIONS);

        assertThat(first.usable()).isTrue();
        assertThat(second.status()).isEqualTo(JevResult.Status.BUDGET);
        assertThat(transport.requests()).hasSize(1);
    }
}
