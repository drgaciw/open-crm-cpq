package com.opencrm.security.client;

import com.opencrm.security.test.TestJwtDecoderConfiguration;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcClientHttpRequestFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The step-2 contract end to end: a calling service's {@code RestClient} with an {@link OutboundAuth} interceptor, and a
 * called service running the baseline chain (in-process through MockMvc) that verifies real RS256 tokens.
 * <ul>
 *   <li>{@code serviceToken()} reaches {@code /internal/**};</li>
 *   <li>{@code relayUserToken()} reaches {@code /api/v1/**} as the user, and is refused on {@code /internal/**};</li>
 *   <li>without a user, {@code relayUserToken()} calls as the service.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ServiceToServiceContractTest.CalledService.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
@DisplayName("ADR-016 D5: service-to-service calls against the baseline chain")
class ServiceToServiceContractTest {

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import({TestJwtDecoderConfiguration.class, Probe.class})
    static class CalledService {
    }

    @RestController
    static class Probe {
        @GetMapping("/api/v1/whoami")
        String whoami() {
            // The actor the called service would record (ADR-016 decision 6).
            return com.opencrm.security.SecurityUtils.getCurrentCaller()
                    .map(com.opencrm.security.Caller::actorId).orElse("anonymous");
        }

        @RequestMapping("/**")
        String any() {
            return "ok";
        }
    }

    /** The calling service's identity: a client-credentials token with the Internal.Invoke app role. */
    private static final ServiceTokenProvider QUOTE_SERVICE =
            () -> Optional.of(TestTokens.service("quote-service", "Internal.Invoke").mint());

    @Autowired
    private WebApplicationContext calledService;

    private MockMvc mvc;
    private final OutboundAuth outboundAuth = new OutboundAuth(QUOTE_SERVICE, true);

    @BeforeEach
    void build() {
        mvc = MockMvcBuilders.webAppContextSetup(calledService)
                .addFilters(calledService.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private record Result(int status, String body) {
    }

    private Result call(ClientHttpRequestInterceptor interceptor, String path) {
        return org.springframework.web.client.RestClient.builder()
                .requestFactory(new MockMvcClientHttpRequestFactory(mvc))
                .requestInterceptor(interceptor)
                .build()
                .get().uri("http://called.test" + path)
                .exchange((request, response) -> new Result(response.getStatusCode().value(),
                        new String(response.getBody().readAllBytes())));
    }

    /** The caller is inside a request authenticated with alice's real token. */
    private static void userRequest() {
        String token = TestTokens.user("alice", "CPQ.User").mint();
        Jwt jwt = Jwt.withTokenValue(token).header("alg", "RS256").subject("alice")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_CPQ_USER")));
    }

    @Test
    @DisplayName("serviceToken() reaches /internal/**")
    void serviceTokenReachesInternal() {
        userRequest();

        assertThat(call(outboundAuth.serviceToken(), "/internal/v1/price-books/resolve-batch").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("relayUserToken() reaches /api/v1/** as the user")
    void relayReachesApiAsUser() {
        userRequest();

        assertThat(call(outboundAuth.relayUserToken(), "/api/v1/whoami")).isEqualTo(new Result(200, TestTokens.oidOf("alice")));
    }

    @Test
    @DisplayName("relayUserToken() on /internal/** is 403: user tokens never carry Internal.Invoke")
    void relayRefusedOnInternal() {
        userRequest();

        assertThat(HttpStatusCode.valueOf(call(outboundAuth.relayUserToken(), "/internal/v1/x").status()))
                .isEqualTo(HttpStatusCode.valueOf(403));
    }

    @Test
    @DisplayName("without a user (consumer, scheduler), relayUserToken() calls as the service")
    void machineContextCallsAsService() {
        assertThat(call(outboundAuth.relayUserToken(), "/api/v1/whoami")).isEqualTo(new Result(200, "service:quote-service"));
    }
}
