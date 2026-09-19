package com.opencrm.pricebook.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the production chain: outside {@code dev}, {@code /api/v1/**} stays authenticated
 * and {@code /internal/**} stays open for service-to-service lookups.
 * Do not weaken this chain when adding the local {@code dev} permitAll split.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityConfigNonDevProfileTest.TestApp.class)
@ActiveProfiles("test")
@DisplayName("pricebook SecurityConfig !dev profile")
class SecurityConfigNonDevProfileTest {

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, ProbeController.class})
    static class TestApp {
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/v1/price-books")
        String list() {
            return "ok";
        }

        @GetMapping("/internal/v1/probe")
        String internal() {
            return "ok";
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("unauthenticated GET /api/v1/price-books is forbidden")
    void unauthenticatedListIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/price-books"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("unauthenticated GET /internal/** stays permitted for mesh callers")
    void internalMeshPathStaysPermitted() throws Exception {
        mockMvc.perform(get("/internal/v1/probe"))
                .andExpect(status().isOk());
    }
}
