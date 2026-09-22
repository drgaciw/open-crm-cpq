package com.opencrm.crm.lead.scoring;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.crm.lead.Lead;

import java.time.Instant;
import java.util.Map;

/**
 * Advisory lead score as exposed by the API (ADR-015). Mirrors the frontend {@code JevSuggestion} model.
 * Absent unless Jev actually scored the lead.
 *
 * @param value the 0-100 score as text
 * @param probabilities probability per rubric level, keyed "0" (lowest) to "4"
 * @param advisory always true
 */
public record LeadScoreSuggestionDto(
        String value,
        double confidence,
        Map<String, Double> probabilities,
        String rubricVersion,
        String model,
        boolean advisory,
        Instant computedAt) {

    public static final String SOURCE_JEV = "JEV";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Double>> PROBABILITIES = new TypeReference<>() { };

    /** @return null when the lead has no Jev score, so the field is omitted from the response */
    public static LeadScoreSuggestionDto from(Lead lead) {
        if (lead == null || !SOURCE_JEV.equals(lead.getScoreSource()) || lead.getScore() == null) {
            return null;
        }
        return new LeadScoreSuggestionDto(
                Integer.toString(lead.getScore()),
                lead.getScoreConfidence() == null ? 0 : lead.getScoreConfidence().doubleValue(),
                probabilities(lead.getScoreProbabilities()),
                lead.getScoreRubricVersion(),
                lead.getScoreModel(),
                true,
                lead.getScoredAt());
    }

    private static Map<String, Double> probabilities(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, PROBABILITIES);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
