package com.opencrm.product.config;

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
 * Production / {@code !dev} must keep {@code /api/v1/**} authenticated.
 * Do not weaken this chain when adding the local {@code dev} permitAll split.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityConfigNonDevProfileTest.TestApp.class)
@ActiveProfiles("test")
@DisplayName("product SecurityConfig !dev profile")
class SecurityConfigNonDevProfileTest {

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, CatalogProbeController.class})
    static class TestApp {
    }

    @RestController
    static class CatalogProbeController {
        @GetMapping("/api/v1/products")
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
    @DisplayName("unauthenticated GET /api/v1/products is forbidden")
    void unauthenticatedCatalogListIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isForbidden());
    }
}
