package com.opencrm.quote.config;

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
 * Same UAT-1 finding as product-service: quote-service had no {@code @Profile("dev")}
 * permitAll chain, so local {@code nx serve crm} traffic is 403/401 without Azure AD.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityConfigDevProfileTest.TestApp.class)
@ActiveProfiles("dev")
@DisplayName("quote SecurityConfig dev profile")
class SecurityConfigDevProfileTest {

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, QuoteProbeController.class})
    static class TestApp {
    }

    @RestController
    static class QuoteProbeController {
        @GetMapping("/api/v1/quotes")
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
    @DisplayName("unauthenticated GET /api/v1/quotes is permitted")
    void unauthenticatedQuoteListIsPermitted() throws Exception {
        mockMvc.perform(get("/api/v1/quotes"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }
}
