package com.opencrm.pricing.renewal;

import com.opencrm.pricing.config.GlobalExceptionHandler;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Resolve stays on the internal chain. Existing user roles and a service without {@code Internal.Invoke} are refused.
 * No Legal or Owner app role exists, so those names are not granted; a token that merely carries them is still refused.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = RenewalResolveSecurityTest.App.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
@Tag("unit")
@DisplayName("renewal resolve authorization")
class RenewalResolveSecurityTest {

    private static final String BODY = """
            {"currency":"USD","exitNet":40.0000,"quantity":1,"termMonths":12,
             "pins":{"actionDate":"2026-08-02","actionPriceBookVersionId":"pb-50","actionListUnit":50,
                     "effectiveDate":"2026-11-01","effectivePriceBookVersionId":"pb-52","effectiveListUnit":52,
                     "lineStart":"2026-12-15","lineStartPriceBookVersionId":"pb-dec","lineStartListUnit":60},
             "rungs":[{"kind":"ACCOUNT","sourceId":"acct-same","mode":"SAME"}]}
            """;

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import({TestJwtDecoderConfiguration.class, RenewalResolveController.class, RenewalResolveService.class,
            RenewalResolveRequestConverter.class, GlobalExceptionHandler.class})
    static class App {
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void anonymousAndExistingUserRolesAreRefused() throws Exception {
        mvc.perform(post(RenewalResolveController.PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        for (String role : new String[] {"CPQ.User", "SalesVP", "DealDesk", "CFO", "Admin", "Legal"}) {
            mvc.perform(post(RenewalResolveController.PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY)
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.user("person", role).bearer()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void serviceWithoutInternalInvokeIsForbiddenAndQuoteServiceWithItIsAPreview() throws Exception {
        mvc.perform(post(RenewalResolveController.PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.service("crm-service").bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(post(RenewalResolveController.PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.service("quote-service", "Internal.Invoke").bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preview").value(true))
                .andExpect(jsonPath("$.data.resolution.resolution").value("PRICED"));
    }
}
