package com.opencrm.jev.answer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.jev.error.JevException;
import com.opencrm.jev.question.JevQuestion;
import com.opencrm.jev.question.JevQuestions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("JevAnswerValidator")
class JevAnswerValidatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final JevQuestion.Choice TIER = JevQuestions.choice("tier", "Which tier?", tiers());
    private static final JevQuestion.Noul SAME = JevQuestions.noul("same", "Same entity?");
    private static final JevQuestion.Score LIKELY =
            JevQuestions.score("likely", "How likely?", List.of("low", "medium", "high"));

    private static Map<String, String> tiers() {
        Map<String, String> tiers = new LinkedHashMap<>();
        tiers.put("MANAGER", "small");
        tiers.put("DIRECTOR", "medium");
        tiers.put("VP_SALES", "large");
        return tiers;
    }

    private static JsonNode json(String text) throws Exception {
        return JSON.readTree(text);
    }

    @Test
    @DisplayName("accepts a choice whose distribution covers exactly the offered keys")
    void acceptsValidChoice() throws Exception {
        JevAnswer answer = JevAnswerValidator.validate(json("""
                {"type":"choice","choice":"DIRECTOR","confidence":0.8,
                 "probabilities":{"MANAGER":0.1,"DIRECTOR":0.8,"VP_SALES":0.1}}"""), TIER);

        assertThat(answer).isInstanceOf(JevAnswer.Choice.class);
        JevAnswer.Choice choice = (JevAnswer.Choice) answer;
        assertThat(choice.choice()).isEqualTo("DIRECTOR");
        assertThat(choice.confidence()).isEqualTo(0.8);
        assertThat(choice.probabilities()).containsOnlyKeys("MANAGER", "DIRECTOR", "VP_SALES");
    }

    @Test
    @DisplayName("tolerates rounding of 0.02 on the distribution sum")
    void toleratesRounding() throws Exception {
        JevAnswer answer = JevAnswerValidator.validate(json("""
                {"choice":"MANAGER","confidence":0.5,
                 "probabilities":{"MANAGER":0.5,"DIRECTOR":0.3,"VP_SALES":0.19}}"""), TIER);

        assertThat(answer).isInstanceOf(JevAnswer.Choice.class);
    }

    @Test
    @DisplayName("rejects a choice that was not offered")
    void rejectsUnofferedChoice() {
        assertThatThrownBy(() -> JevAnswerValidator.validate(json("""
                {"choice":"CFO","confidence":0.9,
                 "probabilities":{"MANAGER":0.0,"DIRECTOR":0.1,"VP_SALES":0.9}}"""), TIER))
                .isInstanceOf(JevException.InvalidAnswer.class);
    }

    @Test
    @DisplayName("rejects a distribution with an extra key")
    void rejectsExtraKey() {
        assertThatThrownBy(() -> JevAnswerValidator.validate(json("""
                {"choice":"MANAGER","confidence":0.9,
                 "probabilities":{"MANAGER":0.7,"DIRECTOR":0.1,"VP_SALES":0.1,"CFO":0.1}}"""), TIER))
                .isInstanceOf(JevException.InvalidAnswer.class);
    }

    @Test
    @DisplayName("rejects a distribution with a missing key")
    void rejectsMissingKey() {
        assertThatThrownBy(() -> JevAnswerValidator.validate(json("""
                {"choice":"MANAGER","confidence":0.9,"probabilities":{"MANAGER":0.9,"DIRECTOR":0.1}}"""), TIER))
                .isInstanceOf(JevException.InvalidAnswer.class);
    }

    @Test
    @DisplayName("rejects a distribution that does not sum to one")
    void rejectsBadSum() {
        assertThatThrownBy(() -> JevAnswerValidator.validate(json("""
                {"choice":"MANAGER","confidence":0.9,
                 "probabilities":{"MANAGER":0.9,"DIRECTOR":0.3,"VP_SALES":0.1}}"""), TIER))
                .isInstanceOf(JevException.InvalidAnswer.class)
                .hasMessageContaining("sum");
    }

    @Test
    @DisplayName("rejects confidence outside [0,1]")
    void rejectsConfidenceOutOfRange() {
        assertThatThrownBy(() -> JevAnswerValidator.validate(json("""
                {"choice":"MANAGER","confidence":1.4,
                 "probabilities":{"MANAGER":1.0,"DIRECTOR":0.0,"VP_SALES":0.0}}"""), TIER))
                .isInstanceOf(JevException.InvalidAnswer.class);
    }

    @Test
    @DisplayName("rejects an answer whose type does not match the question")
    void rejectsTypeMismatch() {
        assertThatThrownBy(() -> JevAnswerValidator.validate(json("""
                {"type":"noul","noul":0.9}"""), TIER))
                .isInstanceOf(JevException.InvalidAnswer.class);
    }

    @Test
    @DisplayName("rejects a missing answer")
    void rejectsMissingAnswer() {
        assertThatThrownBy(() -> JevAnswerValidator.validate(null, TIER))
                .isInstanceOf(JevException.InvalidAnswer.class);
    }

    @Test
    @DisplayName("noul confidence is its distance from 0.5")
    void noulConfidence() throws Exception {
        JevAnswer certain = JevAnswerValidator.validate(json("{\"type\":\"noul\",\"noul\":0.95}"), SAME);
        JevAnswer unsure = JevAnswerValidator.validate(json("{\"noul\":0.5}"), SAME);

        assertThat(certain.confidence()).isCloseTo(0.9, within(1e-9));
        assertThat(unsure.confidence()).isZero();
    }

    @Test
    @DisplayName("rejects a noul outside [0,1]")
    void rejectsNoulOutOfRange() {
        assertThatThrownBy(() -> JevAnswerValidator.validate(json("{\"noul\":1.2}"), SAME))
                .isInstanceOf(JevException.InvalidAnswer.class);
    }

    @Test
    @DisplayName("accepts a score and aligns probabilities with rubric levels")
    void acceptsScore() throws Exception {
        JevAnswer answer = JevAnswerValidator.validate(json("""
                {"type":"score","score":1.5,"confidence":0.7,
                 "probabilities":{"0":0.0,"1":0.5,"2":0.5}}"""), LIKELY);

        JevAnswer.Score score = (JevAnswer.Score) answer;
        assertThat(score.probabilities()).containsExactly(0.0, 0.5, 0.5);
        assertThat(score.normalisedExpectation()).isCloseTo(0.75, within(1e-9));
    }

    @Test
    @DisplayName("rejects a score above the top rubric level")
    void rejectsScoreAboveTop() {
        assertThatThrownBy(() -> JevAnswerValidator.validate(json("""
                {"score":3,"confidence":0.7,"probabilities":{"0":0.0,"1":0.5,"2":0.5}}"""), LIKELY))
                .isInstanceOf(JevException.InvalidAnswer.class);
    }

    @Test
    @DisplayName("aggregate confidence is the minimum, and zero when empty")
    void aggregateIsMinimum() {
        List<JevAnswer> answers = List.of(
                new JevAnswer.Choice("MANAGER", 0.9, Map.of("MANAGER", 1.0)),
                new JevAnswer.Noul(0.6));

        assertThat(JevConfidence.aggregate(answers)).isCloseTo(0.2, within(1e-9));
        assertThat(JevConfidence.aggregate(List.of())).isZero();
    }
}
