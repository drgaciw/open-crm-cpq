package com.opencrm.pricing.cost;

import com.opencrm.security.test.TestJwtDecoderConfiguration;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The cost basis API is Finance data loading: ADR-016 app role {@code Admin} ({@code ROLE_ADMIN}) only, added to the
 * {@code libs/security} baseline chain through an {@code OpenCrmSecurityCustomizer}. Runs the real chain in {@code jwt}
 * mode with this service's {@code application.yml} and real signed tokens; a catch-all probe answers 200, so the
 * status codes come from the chain alone.
 */
@Tag("unit")
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = CostBasisSecurityTest.ProbeApp.class,
        initializers = ConfigDataApplicationContextInitializer.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
@DisplayName("cost basis API: Admin role only, outside dev")
class CostBasisSecurityTest {

    private static final String IMPORTS = "/api/v1/cost-basis/imports";

    /** Not a stereotype, so component scanning elsewhere never picks it up. */
    @EnableWebMvc
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import({TestJwtDecoderConfiguration.class, CostBasisSecurityConfiguration.class})
    static class ProbeApp {
        @Bean
        RouterFunction<ServerResponse> catchAllProbe() {
            return RouterFunctions.route(RequestPredicates.all(), request -> ServerResponse.ok().body("ok"));
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
    @DisplayName("anonymous is 401")
    void anonymous() throws Exception {
        mvc.perform(post(IMPORTS)).andExpect(status().isUnauthorized());
        mvc.perform(get(IMPORTS)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CPQ.User, DealDesk and CFO are 403")
    void nonAdminRolesAreForbidden() throws Exception {
        for (String role : new String[]{"CPQ.User", "DealDesk", "CFO"}) {
            mvc.perform(post(IMPORTS).header(HttpHeaders.AUTHORIZATION, TestTokens.user("bob", role).bearer()))
                    .andExpect(status().isForbidden());
            mvc.perform(get(IMPORTS).header(HttpHeaders.AUTHORIZATION, TestTokens.user("bob", role).bearer()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("a service token with Internal.Invoke is 403: ingestion is not an internal call")
    void serviceTokenIsForbidden() throws Exception {
        mvc.perform(post(IMPORTS)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.service("quote-service", "Internal.Invoke").bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin reaches import and listing")
    void adminIsAllowed() throws Exception {
        mvc.perform(post(IMPORTS).header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice", "Admin").bearer()))
                .andExpect(status().isOk());
        mvc.perform(get(IMPORTS).header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice", "Admin").bearer()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the rule is scoped: other pricing APIs still accept a CPQ.User token")
    void otherApisUnchanged() throws Exception {
        mvc.perform(get("/api/v1/rule-sets").header(HttpHeaders.AUTHORIZATION, TestTokens.user("bob", "CPQ.User").bearer()))
                .andExpect(status().isOk());
    }
}
