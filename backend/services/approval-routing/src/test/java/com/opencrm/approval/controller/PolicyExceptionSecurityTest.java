package com.opencrm.approval.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.approval.config.GlobalExceptionHandler;
import com.opencrm.approval.domain.CommercialPolicy;
import com.opencrm.approval.domain.PolicyException;
import com.opencrm.approval.domain.PolicyStatus;
import com.opencrm.approval.repository.CommercialPolicyRepository;
import com.opencrm.approval.repository.PolicyExceptionRepository;
import com.opencrm.approval.service.PolicyExceptionService;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant and actor negatives for RecordException through the baseline security chain.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = PolicyExceptionSecurityTest.App.class,
        initializers = ConfigDataApplicationContextInitializer.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
@DisplayName("RecordException endpoint: tenant and actor")
class PolicyExceptionSecurityTest {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String TENANT = "00000000-0000-4000-8000-00000000c0de";

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration({OpenCrmSecurityAutoConfiguration.class, OpenCrmActorAutoConfiguration.class})
    @Import({TestJwtDecoderConfiguration.class, PolicyExceptionController.class, PolicyExceptionService.class,
            GlobalExceptionHandler.class})
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
        PolicyExceptionRepository policyExceptionRepository() {
            return Mockito.mock(PolicyExceptionRepository.class);
        }

        @Bean
        CommercialPolicyRepository commercialPolicyRepository() {
            return Mockito.mock(CommercialPolicyRepository.class);
        }

        @Bean
        OutboxEventPublisher outboxEventPublisher() {
            return Mockito.mock(OutboxEventPublisher.class);
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PolicyExceptionRepository exceptions;
    @Autowired
    private CommercialPolicyRepository policies;
    @Autowired
    private OutboxEventPublisher outbox;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        Mockito.reset(exceptions, policies, outbox);
        CommercialPolicy policy = CommercialPolicy.builder().name("Standard").status(PolicyStatus.ACTIVE).build();
        policy.setId(7L);
        when(policies.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(policy));
        when(exceptions.save(any(PolicyException.class))).thenAnswer(invocation -> {
            PolicyException saved = invocation.getArgument(0);
            saved.setId(15L);
            return saved;
        });
    }

    @Test
    @DisplayName("DealDesk records the token oid and emits ExceptionRecorded; a body actor is not a field")
    void dealDeskRecords() throws Exception {
        mvc.perform(post("/api/v1/policy-exceptions")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("desk-1", "DealDesk").bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TENANT)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recordedBy").value("user:" + TestTokens.oidOf("desk-1")))
                .andExpect(jsonPath("$.data.quoteVersion").value(3));
        verify(outbox).publish(any(), any(), org.mockito.ArgumentMatchers.eq(EventContractRegistry.EXCEPTION_RECORDED_V1), any());
    }

    @Test
    @DisplayName("a service token is forbidden and records nothing")
    void serviceTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/policy-exceptions")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.service("quote-service").bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TENANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACTOR_REQUIRED"));
        verify(exceptions, never()).save(any());
    }

    @Test
    @DisplayName("a seller without a deal-desk role is forbidden and records nothing")
    void sellerRoleIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/policy-exceptions")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("seller", "CPQ.User").bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TENANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("EXCEPTION_ROLE_REQUIRED"));
        verify(exceptions, never()).save(any());
    }

    @Test
    @DisplayName("a quote tenant that is not the caller's is forbidden and records nothing")
    void tenantMismatchIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/policy-exceptions")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("desk-1", "DealDesk").bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("other-tenant")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TENANT_MISMATCH"));
        verify(exceptions, never()).save(any());
    }

    @Test
    @DisplayName("an anonymous caller is unauthorized")
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/policy-exceptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TENANT)))
                .andExpect(status().isUnauthorized());
        verify(exceptions, never()).save(any());
    }

    private static String body(String tenantId) {
        return "{\"quoteId\":\"42\",\"quoteVersion\":3,\"policyId\":7,\"justification\":\"one-off\","
                + "\"expiresAt\":\"" + Instant.now().plusSeconds(86_400) + "\",\"tenantId\":\"" + tenantId + "\"}";
    }
}
