package com.opencrm.crm.fact;

import com.opencrm.crm.config.GlobalExceptionHandler;
import com.opencrm.crm.fields.FixtureTenants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FactControllerTest {

    @Mock
    private FactLedgerService factLedgerService;

    @InjectMocks
    private FactController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        signIn(FixtureTenants.A);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void proposeReturnsTheServerScore() throws Exception {
        when(factLedgerService.propose(eq(FactSubjectType.CONTACT), eq(4L), eq(FixtureTenants.A), any()))
                .thenReturn(FactResponse.builder()
                        .id(15L)
                        .stored(true)
                        .score(new BigDecimal("0.9500"))
                        .band(FactBand.VERIFIED)
                        .status(FactStatus.APPLIED)
                        .fieldKey("jobTitle")
                        .value("VP Sales")
                        .evidence(List.of())
                        .build());

        mockMvc.perform(post("/api/v1/contacts/4/facts")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fieldKey":"jobTitle","value":"VP Sales","method":"enrichment",
                                 "evidence":[{"kind":"EXACT_EMAIL_MATCH","url":"https://example.com/p"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.score").value(0.95))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.id").value(15));
    }

    @Test
    void aClientSuppliedScoreIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/contacts/4/facts")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fieldKey":"jobTitle","value":"VP Sales","method":"enrichment","score":0.99,
                                 "evidence":[{"kind":"EXACT_EMAIL_MATCH"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FACT_SCORE_NOT_CLIENT_SUPPLIED"));

        verify(factLedgerService, never()).propose(any(), anyLong(), any(), any());
    }

    @Test
    void aClientSuppliedEvidenceWeightIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/organizations/4/facts")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fieldKey":"industry","value":"Software","method":"enrichment",
                                 "evidence":[{"kind":"CITED_WEB_ANSWER","weight":0.99}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FACT_SCORE_NOT_CLIENT_SUPPLIED"));

        verify(factLedgerService, never()).propose(any(), anyLong(), any(), any());
    }

    @Test
    void listProposalsStaysOnTheSubjectRoute() throws Exception {
        when(factLedgerService.listProposals(FactSubjectType.CONTACT, 4L, FixtureTenants.A))
                .thenReturn(List.of(FactResponse.builder()
                        .id(9L)
                        .stored(true)
                        .status(FactStatus.PROPOSED)
                        .fieldKey("jobTitle")
                        .value("VP Sales")
                        .evidence(List.of())
                        .build()));

        mockMvc.perform(get("/api/v1/contacts/4/facts/proposals").param("tenant", FixtureTenants.A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PROPOSED"))
                .andExpect(jsonPath("$.data[0].value").value("VP Sales"));
    }

    @Test
    void acceptAndDismissDelegateToTheService() throws Exception {
        when(factLedgerService.accept(FactSubjectType.CONTACT, 4L, 9L, FixtureTenants.A))
                .thenReturn(FactResponse.builder()
                        .id(9L)
                        .stored(true)
                        .status(FactStatus.APPLIED)
                        .evidence(List.of())
                        .build());
        when(factLedgerService.dismiss(FactSubjectType.ORGANIZATION, 8L, 3L, FixtureTenants.A))
                .thenReturn(FactResponse.builder()
                        .id(3L)
                        .stored(true)
                        .status(FactStatus.DISMISSED)
                        .evidence(List.of())
                        .build());

        mockMvc.perform(post("/api/v1/contacts/4/facts/9/accept").param("tenant", FixtureTenants.A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"));
        mockMvc.perform(post("/api/v1/organizations/8/facts/3/dismiss").param("tenant", FixtureTenants.A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISMISSED"));
    }

    @Test
    void spoofedTenantQueryParamIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(get("/api/v1/contacts/4/facts/proposals").param("tenant", FixtureTenants.B))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TENANT_MISMATCH"));

        verify(factLedgerService, never()).listProposals(any(), anyLong(), any());
    }

    @Test
    void tenantQueryParamMayBeOmittedWhenJwtTidIsPresent() throws Exception {
        when(factLedgerService.listProposals(FactSubjectType.CONTACT, 4L, FixtureTenants.A))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/contacts/4/facts/proposals"))
                .andExpect(status().isOk());

        verify(factLedgerService).listProposals(FactSubjectType.CONTACT, 4L, FixtureTenants.A);
    }

    private static void signIn(String tid) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("person-oid")
                .claim("oid", "person-oid")
                .claim("scp", "user")
                .claim("tid", tid)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
