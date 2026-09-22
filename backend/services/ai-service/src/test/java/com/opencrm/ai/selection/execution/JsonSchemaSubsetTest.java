package com.opencrm.ai.selection.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class JsonSchemaSubsetTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void requiredPropertiesTypesEnumsAndBoundsAreEnforced() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","required":["discount","currency","lines"],"additionalProperties":false,
                 "properties":{"discount":{"type":"number","minimum":0,"maximum":10},
                    "currency":{"type":"string","enum":["USD"]},
                    "lines":{"type":"array","minItems":1,"maxItems":2,"items":{"type":"integer"}}}}
                """);
        JsonSchemaSubset.checkSchema(schema);
        assertTrue(JsonSchemaSubset.matches(schema, mapper.readTree("{\"discount\":5,\"currency\":\"USD\",\"lines\":[1]}")));
        assertFalse(JsonSchemaSubset.matches(schema, mapper.readTree("{\"discount\":5,\"currency\":\"USD\"}")));
        assertFalse(JsonSchemaSubset.matches(schema, mapper.readTree("{\"discount\":15,\"currency\":\"USD\",\"lines\":[1]}")));
        assertFalse(JsonSchemaSubset.matches(schema, mapper.readTree("{\"discount\":5,\"currency\":\"EUR\",\"lines\":[1]}")));
        assertFalse(JsonSchemaSubset.matches(schema, mapper.readTree("{\"discount\":5,\"currency\":\"USD\",\"lines\":[\"1\"]}")));
        assertFalse(JsonSchemaSubset.matches(schema, mapper.readTree("{\"discount\":5,\"currency\":\"USD\",\"lines\":[1],\"extra\":true}")));
    }

    @Test void unsupportedReferencesAndPatternsAreRejectedUpfront() throws Exception {
        var reference = mapper.readTree("{\"type\":\"object\",\"$ref\":\"https://untrusted.invalid/schema\"}");
        assertThrows(IllegalArgumentException.class, () -> JsonSchemaSubset.checkSchema(reference));
        var pattern = mapper.readTree("{\"type\":\"string\",\"pattern\":\".*\"}");
        assertThrows(IllegalArgumentException.class, () -> JsonSchemaSubset.checkSchema(pattern));
    }
}
