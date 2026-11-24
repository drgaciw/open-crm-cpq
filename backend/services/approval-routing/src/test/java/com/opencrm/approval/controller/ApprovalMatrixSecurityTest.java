package com.opencrm.approval.controller;

import com.opencrm.approval.config.GlobalExceptionHandler;
import com.opencrm.approval.mapper.ApprovalRequestMapperImpl;
import com.opencrm.approval.repository.ApprovalMatrixRepository;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.approval.service.ApprovalMatrixService;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-tenant and actor-role refusals on matrix submit and the approver inbox, through the real security chain.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ApprovalMatrixSecurityTest.App.class,
        initializers = ConfigDataApplicationContextInitializer.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
@DisplayName("Approval matrix submit and inbox refusals")
class ApprovalMatrixSecurityTest {

    private static final String OTHER_TENANT = "11111111-1111-4111-8111-111111111111";

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration({OpenCrmSecurityAutoConfiguration.class, OpenCrmActorAutoConfiguration.class})
    @Import({TestJwtDecoderConfiguration.class, ApprovalMatrixController.class, ApprovalMatrixService.class,
            ApprovalRequestMapperImpl.class, GlobalExceptionHandler.class})
    static class App {
        @Bean
        ApprovalMatrixRepository approvalMatrixRepository() {
            return Mockito.mock(ApprovalMatrixRepository.class);
        }

        @Bean
        ApprovalRequestRepository approvalRequestRepository() {
            return Mockito.mock(ApprovalRequestRepository.class);
        }

        @Bean
        OutboxEventPublisher outboxEventPublisher() {
            return Mockito.mock(OutboxEventPublisher.class);
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ApprovalRequestRepository requests;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        Mockito.reset(requests);
    }

    @Test
    @DisplayName("submit from another tenant is 403 and writes nothing")
    void submitCrossTenant() throws Exception {
        mvc.perform(post("/api/v1/approvals/matrix-submissions")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("seller", "CPQ.User").bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(OTHER_TENANT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CROSS_TENANT"));
        verify(requests, never()).save(any());
    }

    @Test
    @DisplayName("Admin cannot submit, and a service token cannot submit")
    void submitRoleNegatives() throws Exception {
        mvc.perform(post("/api/v1/approvals/matrix-submissions")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("admin", "Admin").bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("REQUESTER_ROLE_REQUIRED"));

        mvc.perform(post("/api/v1/approvals/matrix-submissions")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.service("quote-service", "Internal.Invoke").bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("REQUESTER_MUST_BE_A_USER"));
        verify(requests, never()).save(any());
    }

    @Test
    @DisplayName("a Deal Desk caller cannot open the CFO inbox, and cannot ask for another tenant")
    void inboxRoleAndTenantNegatives() throws Exception {
        mvc.perform(get("/api/v1/approvals/inbox")
                        .param("tier", "CFO")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("desk", "DealDesk").bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("APPROVER_ROLE_REQUIRED"));

        mvc.perform(get("/api/v1/approvals/inbox")
                        .param("tenantId", OTHER_TENANT)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("desk", "DealDesk").bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CROSS_TENANT"));

        mvc.perform(get("/api/v1/approvals/inbox")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.service("quote-service").bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("REQUESTER_MUST_BE_A_USER"));
    }

    private static String body(String tenantOverride) {
        String tenant = tenantOverride == null ? "00000000-0000-4000-8000-00000000c0de" : tenantOverride;
        return """
                {"quoteId":"42","quoteVersion":"7","verdict":"REQUIRES_APPROVAL","quoteTenantId":"%s",
                "breaches":[{"breachType":"DISCOUNT_PERCENT","amount":25000}]}
                """.formatted(tenant);
    }
}
