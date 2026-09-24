package com.opencrm.approval.controller;

import com.opencrm.approval.config.GlobalExceptionHandler;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.mapper.ApprovalRequestMapperImpl;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.approval.service.ApprovalService;
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
import org.mockito.ArgumentCaptor;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-016 step 4 acceptance: an approval decision through the real {@code libs/security} chain with this service's
 * {@code application.yml} (jwt mode) and real RS256 tokens. The controller, service and mapper are the production ones;
 * only the repository and outbox are test doubles, so the test can read what would be persisted.
 *
 * <p>The approver persisted is the token's {@code oid}, whatever the request says, and only a role that satisfies the
 * tier can decide. The quote-service half, that the quote BFF relays the user's token to approval-routing, is
 * {@code RestClientConfigAuthTest} in quote-service.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ApprovalDecisionSecurityTest.App.class,
        initializers = ConfigDataApplicationContextInitializer.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
@DisplayName("ADR-016 step 4: approval decisions through the baseline chain")
class ApprovalDecisionSecurityTest {

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration({OpenCrmSecurityAutoConfiguration.class, OpenCrmActorAutoConfiguration.class})
    @Import({TestJwtDecoderConfiguration.class, ApprovalController.class, ApprovalService.class,
            ApprovalRequestMapperImpl.class, GlobalExceptionHandler.class})
    static class App {

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
    private ApprovalRequestRepository repository;

    private MockMvc mvc;

    private final String requester = TestTokens.oidOf("rep-1");

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        reset(repository);
        ApprovalRequest pending = ApprovalRequest.builder()
                .quoteId("1001").approvalType(ApprovalType.QUOTE).status(ApprovalStatus.PENDING)
                .requestedDiscount(BigDecimal.TEN).requesterId(requester).submittedAt(Instant.now())
                .approvalTier("SALES_VP").build();
        pending.setId(9001L);
        when(repository.findById(9001L)).thenReturn(Optional.of(pending));
        when(repository.save(any(ApprovalRequest.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("the persisted approver is the token's oid; an approverId parameter is ignored")
    void persistedApproverIsTheToken() throws Exception {
        mvc.perform(post("/api/v1/approvals/9001/approve")
                        .param("approverId", "mallory")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("vp-1", "SalesVP").bearer())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approverId").value(TestTokens.oidOf("vp-1")));

        ArgumentCaptor<ApprovalRequest> saved = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getApproverId())
                .isEqualTo(TestTokens.oidOf("vp-1"))
                .isNotEqualTo("vp-1")
                .isNotEqualTo("mallory");
    }

    @Test
    @DisplayName("a role below the tier gets 403 APPROVER_ROLE_REQUIRED and nothing is saved")
    void roleBelowTierIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/approvals/9001/reject")
                        .param("reason", "no")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("desk-1", "DealDesk").bearer())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("APPROVER_ROLE_REQUIRED"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("the requester gets 403 SELF_APPROVAL, and a service token 403 APPROVER_MUST_BE_A_USER")
    void requesterAndServicesCannotDecide() throws Exception {
        mvc.perform(post("/api/v1/approvals/9001/approve")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("rep-1", "SalesVP").bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SELF_APPROVAL"));
        mvc.perform(post("/api/v1/approvals/9001/approve")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.service("quote-service", "Internal.Invoke").bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("APPROVER_MUST_BE_A_USER"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("submit records the token's user as requester; a requesterId in the body is ignored")
    void submitRecordsTheCaller() throws Exception {
        mvc.perform(post("/api/v1/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quoteId":"1001","tier":"DEAL_DESK","requesterId":"someone-else"}""")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("rep-1", "CPQ.User").bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requesterId").value(requester));
    }
}
