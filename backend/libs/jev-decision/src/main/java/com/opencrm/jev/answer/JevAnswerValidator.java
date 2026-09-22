package com.opencrm.jev.answer;

import com.fasterxml.jackson.databind.JsonNode;
import com.opencrm.jev.error.JevException;
import com.opencrm.jev.question.JevQuestion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict validation of a raw answer against the question that was asked. Same contract the UAT client
 * enforces: the distribution must cover exactly the offered keys and sum to one, so a model that drifts
 * off the rubric is rejected rather than interpreted.
 */
public final class JevAnswerValidator {

    /** Slack allowed on a distribution summing to one. */
    public static final double PROBABILITY_TOLERANCE = 0.02;

    private JevAnswerValidator() {
    }

    public static JevAnswer validate(JsonNode raw, JevQuestion question) {
        String id = question.id();
        if (raw == null || !raw.isObject()) {
            throw invalid(id, "answer is missing or not an object");
        }
        String type = raw.path("type").asText(null);
        if (type != null && !type.equals(question.type())) {
            throw invalid(id, "expected type " + question.type() + " but got " + type);
        }
        return switch (question) {
            case JevQuestion.Choice choice -> choice(raw, choice);
            case JevQuestion.Noul noul -> new JevAnswer.Noul(unit(raw.get("noul"), id, "noul"));
            case JevQuestion.Score score -> score(raw, score);
        };
    }

    private static JevAnswer.Choice choice(JsonNode raw, JevQuestion.Choice question) {
        String id = question.id();
        Set<String> keys = question.criteria().keySet();
        JsonNode picked = raw.get("choice");
        if (picked == null || !picked.isTextual() || !keys.contains(picked.asText())) {
            throw invalid(id, "choice is not one of the offered keys");
        }
        double confidence = unit(raw.get("confidence"), id, "confidence");
        Map<String, Double> probabilities = distribution(raw.get("probabilities"), keys, id);
        return new JevAnswer.Choice(picked.asText(), confidence, probabilities);
    }

    private static JevAnswer.Score score(JsonNode raw, JevQuestion.Score question) {
        String id = question.id();
        int levels = question.criteria().size();
        JsonNode value = raw.get("score");
        if (value == null || !value.isNumber() || !Double.isFinite(value.asDouble())
                || value.asDouble() < 0 || value.asDouble() > levels - 1) {
            throw invalid(id, "score is outside 0.." + (levels - 1));
        }
        double confidence = unit(raw.get("confidence"), id, "confidence");
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < levels; i++) {
            keys.add(Integer.toString(i));
        }
        Map<String, Double> byLevel = distribution(raw.get("probabilities"), keys, id);
        List<Double> probabilities = new ArrayList<>(levels);
        for (int i = 0; i < levels; i++) {
            probabilities.add(byLevel.get(Integer.toString(i)));
        }
        return new JevAnswer.Score(value.asDouble(), confidence, probabilities);
    }

    private static Map<String, Double> distribution(JsonNode node, Set<String> expectedKeys, String id) {
        if (node == null || !node.isObject()) {
            throw invalid(id, "probabilities is missing or not an object");
        }
        Map<String, Double> result = new LinkedHashMap<>();
        double sum = 0;
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (!expectedKeys.contains(field.getKey())) {
                throw invalid(id, "probabilities contains a key that was not offered");
            }
            double p = unit(field.getValue(), id, "probability");
            result.put(field.getKey(), p);
            sum += p;
        }
        if (result.size() != expectedKeys.size()) {
            throw invalid(id, "probabilities does not cover every offered key");
        }
        if (Math.abs(sum - 1.0) > PROBABILITY_TOLERANCE) {
            throw invalid(id, "probabilities sum to " + sum + ", not 1");
        }
        return result;
    }

    private static double unit(JsonNode node, String id, String field) {
        if (node == null || !node.isNumber()) {
            throw invalid(id, field + " is missing or not a number");
        }
        double value = node.asDouble();
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw invalid(id, field + " is outside [0,1]");
        }
        return value;
    }

    private static JevException.InvalidAnswer invalid(String id, String reason) {
        return new JevException.InvalidAnswer("Answer " + id + ": " + reason);
    }
}
