package com.opencrm.contract.documents.apply;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.contract.config.GlobalExceptionHandler;
import com.opencrm.contract.controller.DocumentApplyController;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.ApplicationUnitStatus;
import com.opencrm.contract.domain.documents.DocumentApplyLedgerEntry;
import com.opencrm.security.test.TestJwtDecoderConfiguration;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AC-S4-10. DoD v0.3 Core 3, v0.2 add 4, v0.3 add 1, v0.3 add 5. */
@Tag("unit")
class DocumentApplyAuthorizationTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Nested
    @ExtendWith(SpringExtension.class)
    @WebAppConfiguration
    @ContextConfiguration(classes = ChainApp.class)
    @TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
    class ChainRefusals {

        @Autowired
        private WebApplicationContext context;

        @Autowired
        private DocumentApplyOrchestrator orchestrator;

        private MockMvc mvc;

        @BeforeEach
        void setUp() {
            clearInvocations(orchestrator);
            mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                    .build();
        }

        @Test
        void unauthenticatedAndRolesWithoutInternalInvokeAreTheBaselineRefusal() throws Exception {
            UUID id = UUID.randomUUID();
            mvc.perform(post(DocumentApplyController.PATH, id))
                    .andExpect(status().isUnauthorized())
                    .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                            .startsWith("Bearer"));
            for (String role : new String[] {"CPQ.User", "DealDesk", "SalesVP", "CFO", "Admin", "Legal"}) {
                mvc.perform(post(DocumentApplyController.PATH, id)
                                .header(HttpHeaders.AUTHORIZATION, TestTokens.user("document-creator", role).bearer()))
                        .andExpect(status().isForbidden());
            }
            mvc.perform(post(DocumentApplyController.PATH, id)
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.service("quote-service").bearer()))
                    .andExpect(status().isForbidden());
            verify(orchestrator, never()).apply(any());
        }

        @Test
        void creatorWithoutTheRoleIsForbiddenAndNotAuditedHere() throws Exception {
            mvc.perform(post(DocumentApplyController.PATH, UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.user("document-creator", "CPQ.User").bearer()))
                    .andExpect(status().isForbidden());
            verify(orchestrator, never()).apply(any());
        }

        @Test
        void internalInvokeReachesTheOrchestratorWithTheTokenTenant() throws Exception {
            UUID id = UUID.randomUUID();
            when(orchestrator.apply(any())).thenReturn(new ApplyResult(AmendmentDocumentStatus.APPLIED, false));
            mvc.perform(post(DocumentApplyController.PATH, id)
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.service("quote-service", "Internal.Invoke").bearer()))
                    .andExpect(status().isOk());
            verify(orchestrator).apply(argThat(command ->
                    command.amendmentId().equals(id)
                            && command.tenantId().equals(UUID.fromString("00000000-0000-4000-8000-00000000c0de"))
                            && command.authorities().contains("ROLE_INTERNAL_INVOKE")));
        }

        @Test
        void aNonCamPathIsNotThisEndpoint() throws Exception {
            mvc.perform(post("/api/v1/amendments/{amendmentId}:apply", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.service("quote-service", "Internal.Invoke").bearer()))
                    .andExpect(status().is5xxServerError());
            verify(orchestrator, never()).apply(any());
        }

        @Test
        void malformedPinsAreRejectedBeforeApply() throws Exception {
            mvc.perform(post(DocumentApplyController.PATH, UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.service("quote-service", "Internal.Invoke").bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pins\":[{\"unitNo\":0,\"pricebookVersion\":\"pb\"}]}"))
                    .andExpect(status().isBadRequest());
            verify(orchestrator, never()).apply(any());
        }
    }

    /**
     * Security-slice only. {@code @TestConfiguration} is not component-scanned, so this mock
     * orchestrator cannot replace the real bean in {@code @SpringBootTest}.
     */
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import({TestJwtDecoderConfiguration.class, DocumentApplyController.class, GlobalExceptionHandler.class})
    static class ChainApp {
        @Bean
        DocumentApplyOrchestrator documentApplyOrchestrator() {
            return mock(DocumentApplyOrchestrator.class);
        }
    }

    @Nested
    class InCamRefusals {

        @Test
        void missingTenantIsAuditedAndDoesNotApply() {
            ApplyFixture fixture = executedAdd();
            assertThatThrownBy(() -> fixture.orchestrator.apply(new ApplyCommand(
                    fixture.document.getId(), null, "quote-service", "ROLE_INTERNAL_INVOKE", "corr-s4", java.util.Map.of())))
                    .isInstanceOf(DocumentApplyException.class)
                    .extracting(ex -> ((DocumentApplyException) ex).getErrorCode())
                    .isEqualTo("TENANT_REQUIRED");
            assertOneAudit(fixture, "MISSING_TID");
            assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.EXECUTED);
            assertThat(fixture.ledger).isEmpty();
            assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.PENDING);
        }

        @Test
        void aTokenWithoutTidIsPassedThroughAsMissingTenant() {
            DocumentApplyOrchestrator orchestrator = mock(DocumentApplyOrchestrator.class);
            when(orchestrator.apply(any())).thenReturn(new ApplyResult(AmendmentDocumentStatus.EXECUTED, false));
            DocumentApplyController controller = new DocumentApplyController(orchestrator);
            UUID id = UUID.randomUUID();
            Jwt jwt = Jwt.withTokenValue("token")
                    .header("alg", "none")
                    .subject("quote-service")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_INVOKE"))));
            try {
                controller.apply(id, null, null);
            } finally {
                SecurityContextHolder.clearContext();
            }
            verify(orchestrator).apply(argThat(command ->
                    command.amendmentId().equals(id) && command.tenantId() == null));
        }

        @Test
        void crossTenantMatchesTheUnknownDocumentResponseAndWritesOneAudit() {
            ApplyFixture fixture = executedAdd();
            UUID unknown = UUID.randomUUID();
            ResourceNotFoundException missing = catchThrowableOfType(ResourceNotFoundException.class,
                    () -> fixture.orchestrator.apply(new ApplyCommand(unknown, fixture.tenantId, "quote-service",
                            "ROLE_INTERNAL_INVOKE", "corr-s4", java.util.Map.of())));
            assertThat(fixture.events).isEmpty();

            ResourceNotFoundException cross = catchThrowableOfType(ResourceNotFoundException.class,
                    () -> fixture.orchestrator.apply(new ApplyCommand(fixture.document.getId(), UUID.randomUUID(),
                            "quote-service", "ROLE_INTERNAL_INVOKE", "corr-s4", java.util.Map.of())));
            assertThat(cross.getMessage()).isEqualTo(new ResourceNotFoundException("Amendment", fixture.document.getId().toString()).getMessage());
            assertThat(cross.getErrorCode()).isEqualTo(missing.getErrorCode());
            assertThat(cross.getStatus()).isEqualTo(missing.getStatus());
            assertOneAudit(fixture, "CROSS_TENANT");
            assertThat(fixture.ledger).isEmpty();
            assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.EXECUTED);
        }

        @Test
        void nonExecutedDocumentIsRefusedWithOneAudit() {
            ApplyFixture fixture = executedAdd();
            fixture.document.setStatus(AmendmentDocumentStatus.DRAFT);
            assertThatThrownBy(fixture::apply)
                    .isInstanceOf(DocumentApplyException.class)
                    .extracting(ex -> ((DocumentApplyException) ex).getErrorCode())
                    .isEqualTo("ILLEGAL_TRANSITION");
            assertOneAudit(fixture, "NOT_EXECUTED");
            assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.DRAFT);
            assertThat(fixture.ledger).isEmpty();
        }

        @Test
        void digestConflictInsideCamWritesOneAuditAndNoSecondLedgerRow() {
            ApplyFixture fixture = executedAdd();
            fixture.ledger.put(fixture.document.getId() + ":1", DocumentApplyLedgerEntry.builder()
                    .documentId(fixture.document.getId())
                    .unitNo(1)
                    .tenantId(fixture.tenantId)
                    .applyKeyDigest("cd".repeat(32))
                    .createdBy("earlier")
                    .build());
            assertThatThrownBy(fixture::apply).isInstanceOf(DocumentApplyException.class);
            assertOneAudit(fixture, "DIGEST_CONFLICT");
            assertThat(fixture.ledger).hasSize(1);
            assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.EXECUTED);
        }

        private static void assertOneAudit(ApplyFixture fixture, String reason) {
            assertThat(fixture.events).hasSize(1);
            var audit = fixture.events.get(0);
            assertThat(audit.eventType()).isEqualTo(DocumentApplyOrchestrator.EVENT_REFUSED);
            assertThat(audit.reasonCode()).isEqualTo(reason);
            assertThat(audit.documentId()).isEqualTo(fixture.document.getId());
            assertThat(audit.actorId()).isEqualTo("quote-service");
            assertThat(audit.actorRole()).contains("ROLE_INTERNAL_INVOKE");
        }

        private static ApplyFixture executedAdd() {
            ApplyFixture fixture = new ApplyFixture();
            Subscription subscription = fixture.subscription(1L, 3L, START, END);
            fixture.unit(1, subscription, 3L);
            fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
            return fixture;
        }
    }
}
