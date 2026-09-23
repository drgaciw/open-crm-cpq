package com.opencrm.security.web;

import com.opencrm.security.test.TestTokens;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The two chains that exist without an identity provider: explicit {@code dev} mode, and {@code jwt} mode unconfigured. */
@DisplayName("ADR-016 D4: dev mode and an unconfigured jwt mode")
class OpenCrmSecurityDevAndUnconfiguredTest {

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import(Probe.class)
    static class App {
    }

    @RestController
    static class Probe {
        @RequestMapping("/**")
        String any() {
            return "ok";
        }
    }

    static MockMvc mockMvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Nested
    @ExtendWith(SpringExtension.class)
    @WebAppConfiguration
    @ContextConfiguration(classes = App.class)
    @ActiveProfiles("dev")
    @DisplayName("dev profile")
    class DevProfile {
        @Autowired
        WebApplicationContext context;

        @Test
        @DisplayName("permits everything, /internal/** included, without a token")
        void permitsAll() throws Exception {
            MockMvc mvc = mockMvc(context);
            mvc.perform(get("/api/v1/quotes")).andExpect(status().isOk());
            mvc.perform(get("/internal/v1/configurations/evaluate")).andExpect(status().isOk());
        }
    }

    @Nested
    @ExtendWith(SpringExtension.class)
    @WebAppConfiguration
    @ContextConfiguration(classes = App.class)
    @DisplayName("jwt mode with no issuer configured")
    class Unconfigured {
        @Autowired
        WebApplicationContext context;

        @Test
        @DisplayName("starts, keeps health public, and rejects every token with 401")
        void rejectsTokens() throws Exception {
            MockMvc mvc = mockMvc(context);
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
            mvc.perform(get("/api/v1/quotes")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/quotes")
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice", "CPQ.User").bearer()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
