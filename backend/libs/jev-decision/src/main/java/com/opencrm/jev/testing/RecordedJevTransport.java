package com.opencrm.jev.testing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.jev.state.JevFeatureSchema;
import com.opencrm.jev.state.JevFeatureSchema.FeatureType;
import com.opencrm.jev.state.JevState;
import com.opencrm.jev.transport.JevTransport;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Replays answers from a fixture file, keyed by the canonical feature state. This is the offline stage
 * of the ADR-015 rollout: the whole decision path runs with no network and no spend.
 *
 * <p>Fixture shape: {@code {"synthetic": true, "cases": [{"name", "features": {...},
 * "authoritative": "X" | null, "answers": {questionId: {...}}}]}}. Fixtures marked synthetic are
 * hand-made to exercise the pipeline; they say nothing about real model quality.
 */
public final class RecordedJevTransport implements JevTransport {

    /** One fixture case, with its features already passed through the allow-list. */
    public record Case(String name, JevState state, String authoritative) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, JsonNode> answersByState = new HashMap<>();
    private final List<Case> cases = new ArrayList<>();
    private final boolean synthetic;

    public RecordedJevTransport(JevFeatureSchema schema, InputStream fixture) {
        if (fixture == null) {
            throw new IllegalArgumentException("Fixture stream is null; check the resource path");
        }
        JsonNode root;
        try (fixture) {
            root = JSON.readTree(fixture);
        } catch (IOException e) {
            throw new IllegalArgumentException("Fixture is not readable JSON", e);
        }
        this.synthetic = root.path("synthetic").asBoolean(false);
        for (JsonNode node : root.path("cases")) {
            JevState state = state(schema, node.path("features"));
            JsonNode authoritative = node.get("authoritative");
            cases.add(new Case(node.path("name").asText(), state,
                    authoritative == null || authoritative.isNull() ? null : authoritative.asText()));
            answersByState.put(state.canonicalJson(), node.path("answers"));
        }
    }

    private static JevState state(JevFeatureSchema schema, JsonNode features) {
        JevState.Builder builder = JevState.builder(schema);
        for (Map.Entry<String, JsonNode> field : features.properties()) {
            FeatureType type = schema.features().get(field.getKey());
            JsonNode value = field.getValue();
            Object converted;
            if (value.isNull() || type == null) {
                // an undeclared key is handed to the builder so the allow-list rejects it
                converted = value.isNull() ? null : value.asText();
            } else {
                converted = switch (type) {
                    case CODE -> value.asText();
                    case NUMBER -> value.asDouble();
                    case INTEGER -> value.asLong();
                    case BOOLEAN -> value.asBoolean();
                };
            }
            builder.put(field.getKey(), converted);
        }
        return builder.build();
    }

    public List<Case> cases() {
        return List.copyOf(cases);
    }

    public boolean synthetic() {
        return synthetic;
    }

    @Override
    public String name() {
        return "recorded";
    }

    @Override
    public String wireFormat() {
        return "none";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Response decide(Request request) {
        JsonNode answers = answersByState.get(request.state().canonicalJson());
        if (answers == null) {
            throw new IllegalStateException("No recorded answer for state " + request.state().canonicalJson());
        }
        return new Response(answers, null, 1);
    }
}
