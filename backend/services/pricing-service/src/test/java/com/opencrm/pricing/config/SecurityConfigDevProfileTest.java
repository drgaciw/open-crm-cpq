package com.opencrm.pricing.config;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full UAT 2026-09-19: {@code GET /api/v1/rule-sets} and {@code /api/v1/pricing-customers}
 * were 403 in {@code dev} because pricing-service had no {@code @Profile("dev")} permitAll
 * chain (product/quote gained one in UAT-1 #531). Quote Calculate also calls
 * {@code /api/v1/pricing/execute} with no auth header.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityConfigDevProfileTest.TestApp.class)
@ActiveProfiles("dev")
@DisplayName("pricing SecurityConfig dev profile")
class SecurityConfigDevProfileTest {

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, ProbeController.class})
    static class TestApp {
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/v1/rule-sets")
        String list() {
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
    @DisplayName("unauthenticated GET /api/v1/rule-sets is permitted")
    void unauthenticatedListIsPermitted() throws Exception {
        mockMvc.perform(get("/api/v1/rule-sets"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }
}
