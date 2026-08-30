package com.opencrm.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationValidationResultTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void okFactoryIsValidWithEmptyViolations() throws Exception {
        ConfigurationValidationResult result = ConfigurationValidationResult.ok();
        assertTrue(result.isValid());
        assertTrue(result.getViolations().isEmpty());

        String json = mapper.writeValueAsString(result);
        ConfigurationValidationResult roundTrip = mapper.readValue(json, ConfigurationValidationResult.class);
        assertTrue(roundTrip.isValid());
        assertTrue(roundTrip.getViolations().isEmpty());
    }

    @Test
    void invalidFactorySerializesViolations() throws Exception {
        ConfigurationViolation violation = ConfigurationViolation.builder()
                .ruleId(42L)
                .ruleType("REQUIRES")
                .description("Product 1 requires product 2")
                .remediation("Add product 2 to the configuration")
                .build();

        ConfigurationValidationResult result = ConfigurationValidationResult.invalid(List.of(violation));
        assertFalse(result.isValid());
        assertEquals(1, result.getViolations().size());

        String json = mapper.writeValueAsString(result);
        assertTrue(json.contains("\"valid\":false"));
        assertTrue(json.contains("REQUIRES"));
        assertTrue(json.contains("Add product 2"));

        ConfigurationValidationResult roundTrip = mapper.readValue(json, ConfigurationValidationResult.class);
        assertFalse(roundTrip.isValid());
        assertEquals(42L, roundTrip.getViolations().get(0).getRuleId());
        assertEquals("REQUIRES", roundTrip.getViolations().get(0).getRuleType());
    }
}
