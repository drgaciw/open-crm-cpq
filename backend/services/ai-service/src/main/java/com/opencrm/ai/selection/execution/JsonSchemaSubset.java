package com.opencrm.ai.selection.execution;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** Explicit closed subset; unsupported schema semantics are rejected before spending. */
public final class JsonSchemaSubset {
    private static final Set<String> KEYWORDS = Set.of("type", "properties", "required", "additionalProperties",
            "items", "enum", "minimum", "maximum", "minItems", "maxItems", "minLength", "maxLength", "description", "title");
    private JsonSchemaSubset() { }

    public static void checkSchema(JsonNode schema) { checkSchema(schema, 0); }
    private static void checkSchema(JsonNode schema, int depth) {
        if (depth > 32 || schema == null || !schema.isObject()) fail();
        schema.fieldNames().forEachRemaining(key -> { if (!KEYWORDS.contains(key)) fail(); });
        String type = schema.path("type").asText();
        if (!Set.of("object", "array", "string", "integer", "number", "boolean", "null").contains(type)) fail();
        if (schema.has("enum") && (!schema.get("enum").isArray() || schema.get("enum").isEmpty())) fail();
        if (schema.has("minimum") && !schema.get("minimum").isNumber()) fail();
        if (schema.has("maximum") && !schema.get("maximum").isNumber()) fail();
        for (String bound : Set.of("minItems", "maxItems", "minLength", "maxLength")) {
            if (schema.has(bound) && (!schema.get(bound).isIntegralNumber() || !schema.get(bound).canConvertToInt()
                    || schema.get(bound).intValue() < 0)) fail();
        }
        if (schema.has("properties")) {
            if (!schema.path("properties").isObject()) fail();
            schema.path("properties").elements().forEachRemaining(child -> checkSchema(child, depth + 1));
        }
        if (schema.has("required")) {
            if (!schema.path("required").isArray()) fail();
            for (JsonNode key : schema.path("required")) if (!key.isTextual()) fail();
        }
        if (schema.has("additionalProperties") && !schema.path("additionalProperties").isBoolean()) fail();
        if (type.equals("array")) {
            if (!schema.has("items")) fail();
            checkSchema(schema.get("items"), depth + 1);
        }
    }

    public static boolean matches(JsonNode schema, JsonNode value) {
        if (value == null) return false;
        boolean typeMatches = switch (schema.path("type").asText()) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> false;
        };
        if (!typeMatches) return false;
        if (schema.has("enum")) {
            boolean found = false;
            for (JsonNode allowed : schema.get("enum")) if (allowed.equals(value)) found = true;
            if (!found) return false;
        }
        if (value.isObject()) {
            for (JsonNode required : schema.path("required")) if (!value.has(required.asText())) return false;
            var names = value.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                JsonNode child = schema.path("properties").get(name);
                if (child == null && !schema.path("additionalProperties").asBoolean(true)) return false;
                if (child != null && !matches(child, value.get(name))) return false;
            }
        }
        if (value.isArray()) {
            if (!bounds(schema, "minItems", "maxItems", value.size())) return false;
            for (JsonNode child : value) if (!matches(schema.path("items"), child)) return false;
        }
        if (value.isTextual() && !bounds(schema, "minLength", "maxLength",
                value.asText().codePointCount(0, value.asText().length()))) return false;
        if (value.isNumber()) {
            if (schema.has("minimum") && value.decimalValue().compareTo(schema.get("minimum").decimalValue()) < 0) return false;
            if (schema.has("maximum") && value.decimalValue().compareTo(schema.get("maximum").decimalValue()) > 0) return false;
        }
        return true;
    }

    private static boolean bounds(JsonNode schema, String min, String max, int length) {
        return (!schema.has(min) || length >= schema.get(min).intValue()) && (!schema.has(max) || length <= schema.get(max).intValue());
    }
    private static void fail() { throw new IllegalArgumentException("Unsupported or malformed JSON schema"); }
}
