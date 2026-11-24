package com.opencrm.quote.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.config.GlobalExceptionHandler;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.ramp.RampScheduleGuard;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = QuotePolicyEvaluationEndpointTest.App.class,
        initializers = ConfigDataApplicationContextInitializer.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
@DisplayName("POST /api/v1/quotes/{id}/policy-evaluation")
class QuotePolicyEvaluationEndpointTest {

    private static final String TENANT = "00000000-0000-4000-8000-00000000c0de";

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration({OpenCrmSecurityAutoConfiguration.class, OpenCrmActorAutoConfiguration.class})
    @Import({TestJwtDecoderConfiguration.class, ApprovalController.class, GlobalExceptionHandler.class})
    static class App implements WebMvcConfigurer {
        @Bean
        QuoteRepository quoteRepository() {
            return Mockito.mock(QuoteRepository.class);
        }

        @Bean
        ApprovalRoutingClient approvalRoutingClient() {
            return Mockito.mock(ApprovalRoutingClient.class);
        }

        @Bean
        RampScheduleGuard rampScheduleGuard() {
            return Mockito.mock(RampScheduleGuard.class);
        }

        @Bean
        QuotePolicyEvaluation quotePolicyEvaluation() {
            return Mockito.mock(QuotePolicyEvaluation.class);
        }

        @Bean
        ApprovalRequestRepository approvalRequestRepository() {
            return Mockito.mock(ApprovalRequestRepository.class);
        }

        @Bean
        ApprovalService approvalService(
                ApprovalRequestRepository approvalRequestRepository,
                QuoteRepository quoteRepository,
                ApprovalRoutingClient approvalRoutingClient,
                RampScheduleGuard rampScheduleGuard,
                QuotePolicyEvaluation quotePolicyEvaluation) {
            return new ApprovalService(approvalRequestRepository, quoteRepository, approvalRoutingClient, true,
                    rampScheduleGuard, quotePolicyEvaluation);
        }

        @Override
        public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
            converters.add(0, new MappingJackson2HttpMessageConverter(
                    new ObjectMapper().registerModule(new JavaTimeModule())));
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private QuoteRepository quotes;
    @Autowired
    private QuotePolicyEvaluation evaluation;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(quotes, evaluation);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void sellerInTheQuoteTenantReceivesTheVerdict() throws Exception {
        Quote quote = quote(TENANT);
        when(quotes.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote));
        when(evaluation.evaluate(quote)).thenReturn(new QuotePolicyVerdict(true, "REQUIRES_APPROVAL", 3L,
                List.of(new QuotePolicyVerdict.Breach(7L, 1, "DISCOUNT_PERCENT", "32", "25"))));

        mvc.perform(post("/api/v1/quotes/42/policy-evaluation")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("seller", "CPQ.User").bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict").value("REQUIRES_APPROVAL"))
                .andExpect(jsonPath("$.data.breaches[0].actual").value("32"))
                .andExpect(jsonPath("$.data.breaches[0].limit").value("25"));
    }

    @Test
    void serviceTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/quotes/42/policy-evaluation")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.service("quote-service").bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACTOR_REQUIRED"));
        verify(evaluation, never()).evaluate(any());
        verify(quotes, never()).findByIdAndDeletedFalse(any());
    }

    @Test
    void otherTenantIsForbidden() throws Exception {
        when(quotes.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote("other-tenant")));

        mvc.perform(post("/api/v1/quotes/42/policy-evaluation")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("seller", "CPQ.User").bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TENANT_MISMATCH"));
        verify(evaluation, never()).evaluate(any());
    }

    @Test
    void anonymousCallerIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/quotes/42/policy-evaluation"))
                .andExpect(status().isUnauthorized());
        verify(evaluation, never()).evaluate(any());
    }

    private static Quote quote(String tenantId) {
        Quote quote = Quote.builder().quoteNumber("Q-42").tenantId(tenantId).build();
        quote.setId(42L);
        quote.setVersion(3L);
        return quote;
    }
}
