package com.opencrm.security.test;

import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-016 rollout step 0: the prod-profile slice test of one service. A subclass per service runs the
 * {@code libs/security} baseline chain with <em>that service's own</em> {@code application.yml} (loaded by
 * {@link ConfigDataApplicationContextInitializer}, no Spring profile, so {@code jwt} mode) and real signed tokens from
 * {@link TestTokens}. Only the token key source is a test double ({@link TestJwtDecoderConfiguration}).
 *
 * <p>Every request reaches a catch-all probe controller, so the status codes come from the security chain alone:
 * <ul>
 *   <li>health is public; anonymous, expired, forged and wrong-audience requests get 401 with a Bearer challenge;</li>
 *   <li>a user token reaches the user API;</li>
 *   <li>{@code /internal/**} needs the {@code Internal.Invoke} service role: anonymous gets 401, a user token 403,
 *       and a service token 200.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = AbstractSecurityBaselineTest.BaselineProbeApp.class,
        initializers = ConfigDataApplicationContextInitializer.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
public abstract class AbstractSecurityBaselineTest {

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import({TestJwtDecoderConfiguration.class, CatchAllProbe.class})
    static class BaselineProbeApp {
    }

    @RestController
    static class CatchAllProbe {
        @RequestMapping("/**")
        String probe() {
            return "ok";
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    /** A real user-facing path of the service, for example {@code /api/v1/quotes}. */
    protected abstract String userApiPath();

    /** A path under {@code /internal/**}; the service need not have one yet. */
    protected String internalPath() {
        return "/internal/v1/probe";
    }

    @BeforeEach
    void buildMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    @DisplayName("health and info are public")
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("anonymous user API call is 401 with a Bearer challenge")
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get(userApiPath())).andExpect(bearerChallenge());
    }

    @Test
    @DisplayName("a valid user token reaches the user API")
    void userTokenIsAccepted() throws Exception {
        mvc.perform(get(userApiPath()).header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice", "CPQ.User").bearer()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("expired, forged, wrong-audience and wrong-issuer tokens are 401")
    void invalidTokensAreUnauthorized() throws Exception {
        String[] invalid = {
                TestTokens.user("alice", "CPQ.User").expiresAt(Instant.now().minusSeconds(600)).bearer(),
                TestTokens.user("alice", "CPQ.User").signedByForeignKey().bearer(),
                TestTokens.user("alice", "CPQ.User").audience("api://some-other-api").bearer(),
                TestTokens.user("alice", "CPQ.User").issuer("https://login.microsoftonline.com/common/v2.0").bearer(),
                "Bearer not-a-jwt"
        };
        for (String authorization : invalid) {
            mvc.perform(get(userApiPath()).header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(bearerChallenge());
        }
    }

    @Test
    @DisplayName("/internal/** with a user token (no Internal.Invoke) is 403")
    void internalWithUserTokenIsForbidden() throws Exception {
        mvc.perform(get(internalPath()).header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice", "CPQ.User").bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/internal/** with an Internal.Invoke service token is allowed")
    void internalWithServiceTokenIsAllowed() throws Exception {
        mvc.perform(get(internalPath())
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.service("quote-service", "Internal.Invoke").bearer()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("anonymous /internal/** is 401")
    void anonymousInternal() throws Exception {
        mvc.perform(get(internalPath())).andExpect(bearerChallenge());
    }

    private static ResultMatcher bearerChallenge() {
        return result -> {
            status().isUnauthorized().match(result);
            header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")).match(result);
        };
    }
}
