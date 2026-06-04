package com.opencrm.gateway.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class FrontendOriginValidatorTest {

    @Test
    void acceptsExactHttpsOrigin() {
        assertDoesNotThrow(() -> FrontendOriginValidator.validateOriginFormat(
                "https://opencrm-staging.azurewebsites.net"));
    }

    @Test
    void acceptsLocalhostWithPort() {
        assertDoesNotThrow(() -> FrontendOriginValidator.validateOriginFormat("http://localhost:4200"));
    }

    @Test
    void rejectsWildcardOrigin() {
        assertThrows(IllegalStateException.class,
                () -> FrontendOriginValidator.validateOriginFormat("https://*.azurewebsites.net"));
    }

    @Test
    void rejectsOriginWithPath() {
        assertThrows(IllegalStateException.class,
                () -> FrontendOriginValidator.validateOriginFormat("https://app.example.com/crm"));
    }

    @Test
    void rejectsInvalidUri() {
        assertThrows(IllegalStateException.class,
                () -> FrontendOriginValidator.validateOriginFormat("not-a-uri"));
    }
}
