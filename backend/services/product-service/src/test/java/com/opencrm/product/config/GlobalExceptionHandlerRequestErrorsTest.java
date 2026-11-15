package com.opencrm.product.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UAT 2026-11-15 (Jev + Playwright MCP): a malformed request is the caller's error. As in quote-service and CAM, a missing required parameter and a
 * path variable of the wrong type were answered HTTP 500 INTERNAL_SERVER_ERROR by the catch-all.
 */
class GlobalExceptionHandlerRequestErrorsTest {

    @RestController
    static class ProbeController {
        @GetMapping("/probe/items")
        String byAccount(@RequestParam String accountId) {
            return accountId;
        }

        @GetMapping("/probe/items/{id}")
        String byId(@PathVariable Long id) {
            return String.valueOf(id);
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void missingRequiredParameterIsBadRequest() throws Exception {
        mvc.perform(get("/probe/items"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Required request parameter 'accountId' is missing"));
    }

    @Test
    void pathVariableOfTheWrongTypeIsBadRequest() throws Exception {
        mvc.perform(get("/probe/items/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Invalid value 'search' for 'id'"));
    }
}
