package com.opencrm.security.web;

import com.opencrm.security.test.TestTokens;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The production decoder end to end: {@link OpenCrmJwtDecoders#create} fetches the signing key from a JWKS endpoint (a
 * local HTTP server standing in for the Entra tenant), and the baseline chain turns the token into a principal whose
 * name is {@code sub} and whose authorities include the mapped app roles. Also proves that a service can add rules
 * with {@link OpenCrmSecurityCustomizer} but cannot open {@code /internal/**} with one.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = OpenCrmSecurityJwksTest.App.class)
@DisplayName("ADR-016 D4: baseline chain with the production decoder and a JWKS endpoint")
class OpenCrmSecurityJwksTest {

    private static final AtomicInteger JWKS_REQUESTS = new AtomicInteger();
    private static final HttpServer JWKS = startJwksServer();

    @DynamicPropertySource
    static void tenant(DynamicPropertyRegistry registry) {
        registry.add("opencrm.security.jwt.issuer-uri", () -> TestTokens.ISSUER);
        registry.add("opencrm.security.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/discovery/v2.0/keys");
    }

    @AfterAll
    static void stopJwks() {
        JWKS.stop(0);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import(Probe.class)
    static class App {
        @Bean
        OpenCrmSecurityCustomizer adminOnlyRule() {
            return rules -> rules.requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
        }

        @Bean
        OpenCrmSecurityCustomizer attemptToOpenInternal() {
            return rules -> rules.requestMatchers("/internal/**").permitAll();
        }
    }

    @RestController
    static class Probe {
        @GetMapping("/api/v1/whoami")
        String whoami() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            // Spring Security 7 also adds the factor authority FACTOR_BEARER; only the app roles matter here.
            return authentication.getName() + " " + authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority).filter(authority -> authority.startsWith("ROLE_"))
                    .sorted().collect(Collectors.joining(","));
        }

        @RequestMapping("/**")
        String any() {
            return "ok";
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void buildMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    @DisplayName("a token signed by the tenant key becomes principal sub with ROLE_ authorities")
    void principalFromToken() throws Exception {
        mvc.perform(get("/api/v1/whoami")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice", "CPQ.User", "DealDesk").bearer()))
                .andExpect(status().isOk())
                .andExpect(content().string("alice ROLE_CPQ_USER,ROLE_DEAL_DESK"));
        assertThat(JWKS_REQUESTS).as("keys come from the JWKS endpoint").hasPositiveValue();
    }

    @Test
    @DisplayName("a forged or wrong-audience token is 401 with a Bearer challenge")
    void rejected() throws Exception {
        mvc.perform(get("/api/v1/whoami")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice").signedByForeignKey().bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));
        mvc.perform(get("/api/v1/whoami")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice").audience("api://x").bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a service may add a rule: /api/v1/admin/** needs the Admin app role")
    void customizerAddsRule() throws Exception {
        mvc.perform(get("/api/v1/admin/settings")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice", "CPQ.User").bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/settings")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("root", "Admin").bearer()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a customizer cannot open /internal/**: the baseline rule is evaluated first")
    void customizerCannotOpenInternal() throws Exception {
        mvc.perform(get("/internal/v1/anything")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/anything")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice", "CPQ.User").bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("stateless: no session cookie is issued")
    void stateless() throws Exception {
        mvc.perform(get("/api/v1/whoami")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice", "CPQ.User").bearer()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    private static HttpServer startJwksServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/discovery/v2.0/keys", exchange -> {
                JWKS_REQUESTS.incrementAndGet();
                byte[] body = TestTokens.jwkSetJson().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
