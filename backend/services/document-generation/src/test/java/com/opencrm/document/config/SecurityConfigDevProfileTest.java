package com.opencrm.document.config;

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
 * ADR-016 phase 0: {@code document-generation} had a single unconditional chain with no authentication
 * mechanism, so local {@code dev} callers (the quote BFF, {@code nx serve crm}) got 403 on
 * every {@code /api/v1/**} call. Follows the pricing/pricebook precedent (4e88d89).
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityConfigDevProfileTest.TestApp.class)
@ActiveProfiles("dev")
@DisplayName("document-generation SecurityConfig dev profile")
class SecurityConfigDevProfileTest {

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, ProbeController.class})
    static class TestApp {
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/v1/documents")
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
    @DisplayName("unauthenticated GET /api/v1/documents is permitted")
    void unauthenticatedListIsPermitted() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }
}