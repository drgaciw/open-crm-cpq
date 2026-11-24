package com.opencrm.approval.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.approval.config.GlobalExceptionHandler;
import com.opencrm.approval.repository.CommercialPolicyRepository;
import com.opencrm.approval.repository.PolicyExceptionRepository;
import com.opencrm.approval.service.CommercialPolicyService;
import com.opencrm.approval.service.QuotePolicyEvaluationService;
import com.opencrm.audit.AuditTrailService;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.security.OpenCrmActorAutoConfiguration;
import com.opencrm.security.test.TestJwtDecoderConfiguration;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant and actor negatives for the exposed EvaluateQuote endpoint.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = QuotePolicyEvaluationSecurityTest.App.class,
        initializers = ConfigDataApplicationContextInitializer.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
@DisplayName("EvaluateQuote endpoint: tenant and actor")
class QuotePolicyEvaluationSecurityTest {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String TENANT = "00000000-0000-4000-8000-00000000c0de";

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration({OpenCrmSecurityAutoConfiguration.class, OpenCrmActorAutoConfiguration.class})
    @Import({TestJwtDecoderConfiguration.class, CommercialPolicyController.class,
            QuotePolicyEvaluationService.class, GlobalExceptionHandler.class})
    static class App implements WebMvcConfigurer {

        @Bean
        ObjectMapper objectMapper() {
            return JSON;
        }

        @Override
        public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
            converters.add(0, new MappingJackson2HttpMessageConverter(JSON));
        }

        @Bean
        CommercialPolicyRepository commercialPolicyRepository() {
            return Mockito.mock(CommercialPolicyRepository.class);
        }

        @Bean
        PolicyExceptionRepository policyExceptionRepository() {
            return Mockito.mock(PolicyExceptionRepository.class);
        }

        @Bean
        CommercialPolicyService commercialPolicyService() {
            return Mockito.mock(CommercialPolicyService.class);
        }

        @Bean
        OutboxEventPublisher outboxEventPublisher() {
            return Mockito.mock(OutboxEventPublisher.class);
        }

        @Bean
        AuditTrailService auditTrailService() {
            return Mockito.mock(AuditTrailService.class);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OutboxEventPublisher outbox;

    @Autowired
    private CommercialPolicyRepository policies;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        Mockito.reset(outbox, policies);
        when(policies.findByStatusAndDeletedFalse(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("a user in the quote tenant gets a verdict and a QuoteEvaluated event")
    void userEvaluates() throws Exception {
        mvc.perform(post("/api/v1/commercial-policies/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("seller", "CPQ.User").bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TENANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict").value("PASS"));
        verify(outbox).publish(eq("Quote"), eq("42"), eq(EventContractRegistry.QUOTE_EVALUATED_V1), any(), any());
    }

    @Test
    @DisplayName("a service token is forbidden and records nothing")
    void serviceTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/commercial-policies/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.service("quote-service").bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TENANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACTOR_REQUIRED"));
        verify(outbox, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a quote tenant that is not the caller's is forbidden and records nothing")
    void tenantMismatchIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/commercial-policies/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("seller", "CPQ.User").bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("other-tenant")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TENANT_MISMATCH"));
        verify(outbox, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an anonymous caller is unauthorized")
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/commercial-policies/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TENANT)))
                .andExpect(status().isUnauthorized());
        verify(outbox, never()).publish(any(), any(), any(), any(), any());
    }

    private static String body(String tenantId) {
        return "{\"quoteId\":\"42\",\"quoteVersion\":3,\"tenantId\":\"" + tenantId
                + "\",\"discountPercent\":10,\"marginPercent\":40,\"termMonths\":12}";
    }
}
