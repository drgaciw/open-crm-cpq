package com.opencrm.jev.shadow;

import com.opencrm.jev.JevResult;
import com.opencrm.jev.answer.JevAnswer;
import com.opencrm.jev.question.JevQuestion;
import com.opencrm.jev.state.JevState;

import java.util.Objects;

/**
 * One row of {@code jev_decision_log}: what was asked, what came back, and what the authoritative rule
 * or user decided. Holds allow-listed features only; never a raw request or response.
 *
 * @param featuresJson canonical feature JSON, or null when the use case must not persist them
 * @param answerValue chosen key, probability of true, or score, as text; null when there is no answer
 * @param probabilitiesJson answer distribution as JSON, or null
 * @param authoritativeOutcome what the rule or user decided, as text; null when not yet known
 * @param agreement null until an authoritative outcome exists
 */
public record JevDecisionRecord(
        String useCase,
        String subjectType,
        String subjectId,
        String questionId,
        String questionType,
        String evaluatorModel,
        String rubricVersion,
        String transport,
        String wireFormat,
        String featureSchema,
        int featureSchemaVersion,
        String featuresHash,
        String featuresJson,
        String answerValue,
        Double confidence,
        String probabilitiesJson,
        String authoritativeOutcome,
        AuthoritativeSource authoritativeSource,
        Boolean agreement,
        String status,
        String errorClass,
        long latencyMs,
        int attempts,
        String usageJson) {

    public enum AuthoritativeSource {
        RULE, USER, NONE
    }

    /**
     * Builds the row for one question of a result.
     *
     * @param persistFeatures false for use cases whose features must not be stored (duplicate names mode)
     */
    public static JevDecisionRecord of(String useCase, String subjectType, String subjectId,
                                       JevQuestion question, JevState state, JevResult result,
                                       String authoritativeOutcome, AuthoritativeSource source,
                                       boolean persistFeatures) {
        JevAnswer answer = result.answers().get(question.id());
        String value = answerValue(answer);
        Boolean agreement = authoritativeOutcome == null || value == null
                ? null
                : Objects.equals(authoritativeOutcome, value);
        return new JevDecisionRecord(
                useCase, subjectType, subjectId, question.id(), question.type(),
                blankToUnset(result.model()), blankToUnset(result.rubricVersion()),
                result.transport(), result.wireFormat(),
                state.schema().name(), state.schema().version(), state.sha256(),
                persistFeatures ? state.canonicalJson() : null,
                value, answer == null ? null : answer.confidence(), probabilitiesJson(answer),
                authoritativeOutcome, source, agreement,
                result.status().name(), result.errorClass(), result.latencyMs(), result.attempts(),
                result.usage() == null ? null : result.usage().toString());
    }

    static String answerValue(JevAnswer answer) {
        return switch (answer) {
            case null -> null;
            case JevAnswer.Choice choice -> choice.choice();
            case JevAnswer.Noul noul -> Double.toString(noul.pTrue());
            case JevAnswer.Score score -> Double.toString(score.score());
        };
    }

    private static String probabilitiesJson(JevAnswer answer) {
        return switch (answer) {
            case null -> null;
            case JevAnswer.Choice choice -> JsonText.of(choice.probabilities());
            case JevAnswer.Noul noul -> JsonText.of(java.util.Map.of("true", noul.pTrue()));
            case JevAnswer.Score score -> JsonText.of(score.probabilities());
        };
    }

    /** The pinned columns are NOT NULL; a disabled evaluation has no model to report. */
    private static String blankToUnset(String value) {
        return value == null || value.isBlank() ? "unset" : value;
    }
}
