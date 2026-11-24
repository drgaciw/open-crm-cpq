package com.opencrm.product.configrule;

import com.opencrm.product.config.GlobalExceptionHandler;
import com.opencrm.security.test.TestJwtDecoderConfiguration;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C16/C17 for {@code PUT /api/v1/configuration/rules/{id}}. Admin is the only allowed role.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ConfigurationRuleUpdateSecurityTest.App.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
@Tag("unit")
class ConfigurationRuleUpdateSecurityTest {

    private static final String PATH = "/api/v1/configuration/rules/3";
    private static final String BODY = """
            {"name":"Retired rule","ruleType":"REQUIRES","targetProductId":13,"priority":10,
             "status":"RETIRED","errorMessage":"Old rule"}
            """;

    private static final ConfigurationRuleService RULES = mock(ConfigurationRuleService.class);

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ComponentScan(basePackages = "com.opencrm.product.security")
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import({TestJwtDecoderConfiguration.class, GlobalExceptionHandler.class, ConfigurationRuleController.class})
    static class App {
        @Bean
        ConfigurationRuleService configurationRuleService() {
            return RULES;
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        reset(RULES);
        when(RULES.update(any(), any())).thenReturn(ConfigurationRuleDto.builder().id(3L).name("Retired rule").build());
    }

    @Test
    void anonymousRuleUpdateIs401() throws Exception {
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(RULES, never()).update(any(), any());
    }

    @ParameterizedTest(name = "{0} rule update is 403")
    @ValueSource(strings = {"CPQ.User", "DealDesk", "SalesVP", "CFO", "InstalledBase.Read", "NotARealRole"})
    void refusedRoleRuleUpdateIs403(String role) throws Exception {
        bearer(TestTokens.user("alice", role).bearer());
        verify(RULES, never()).update(any(), any());
    }

    @Test
    void rolelessRuleUpdateIs403() throws Exception {
        bearer(TestTokens.user("alice").bearer());
        verify(RULES, never()).update(any(), any());
    }

    @Test
    void internalInvokeRuleUpdateIs403() throws Exception {
        bearer(TestTokens.service("catalog", "Internal.Invoke").bearer());
        verify(RULES, never()).update(any(), any());
    }

    @Test
    void adminRuleUpdateSucceeds() throws Exception {
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("ada", "Admin").bearer()))
                .andExpect(status().isOk());
        verify(RULES).update(any(), any());
    }

    private void bearer(String bearer) throws Exception {
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
    }
}
