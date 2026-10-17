package com.opencrm.contract.controller;

import com.opencrm.contract.config.GlobalExceptionHandler;
import com.opencrm.contract.service.RenewalPolicyRecordService;
import com.opencrm.security.web.EntraRoleAuthorities;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("unit")
class RenewalPolicyControllerTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-00000000c0de");
    private static final UUID ACCOUNT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Test
    void controllerIsReadOnlyAndTakesTenantFromTheToken() throws Exception {
        assertThat(RenewalPolicyController.class.getDeclaredMethods())
                .noneMatch(method -> method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PutMapping.class)
                        || method.isAnnotationPresent(PatchMapping.class)
                        || method.isAnnotationPresent(DeleteMapping.class));
        RenewalPolicyRecordService service = mock(RenewalPolicyRecordService.class);
        when(service.read(TENANT, ACCOUNT, null, null, LocalDate.of(2026, 11, 1))).thenReturn(List.of());
        var mvc = MockMvcBuilders.standaloneSetup(new RenewalPolicyController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(token(TENANT));
        try {
            mvc.perform(get(RenewalPolicyController.PATH)
                            .param("accountId", ACCOUNT.toString())
                            .param("asOf", "2026-11-01")
                            .param("tenantId", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        } finally {
            SecurityContextHolder.clearContext();
        }
        verify(service).read(TENANT, ACCOUNT, null, null, LocalDate.of(2026, 11, 1));
        verifyNoMoreInteractions(service);
        assertThat(EntraRoleAuthorities.authorityFor("Internal.Invoke")).isEqualTo("ROLE_INTERNAL_INVOKE");
    }

    @Test
    void aCallerWithoutATenantClaimIsForbidden() throws Exception {
        RenewalPolicyRecordService service = mock(RenewalPolicyRecordService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new RenewalPolicyController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("nobody", "n/a", "ROLE_INTERNAL_INVOKE"));
        try {
            mvc.perform(get(RenewalPolicyController.PATH).param("accountId", ACCOUNT.toString()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("TENANT_REQUIRED"));
        } finally {
            SecurityContextHolder.clearContext();
        }
        verifyNoMoreInteractions(service);
    }

    private static JwtAuthenticationToken token(UUID tenant) {
        Jwt jwt = Jwt.withTokenValue("synthetic")
                .header("alg", "none")
                .claim("tid", tenant.toString())
                .claim("idtyp", "app")
                .issuedAt(Instant.parse("2026-10-17T00:00:00Z"))
                .expiresAt(Instant.parse("2026-10-17T01:00:00Z"))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_INVOKE")), "quote-service");
    }
}
