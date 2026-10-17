package com.opencrm.contract.controller;

import com.opencrm.contract.config.GlobalExceptionHandler;
import com.opencrm.contract.domain.documents.RenewalPolicyRecord;
import com.opencrm.contract.domain.documents.RenewalPolicyScopeKind;
import com.opencrm.contract.service.RenewalPolicyDraft;
import com.opencrm.contract.service.RenewalPolicyRecordService;
import com.opencrm.contract.service.RenewalPolicyRecords;
import com.opencrm.contract.service.RenewalPolicyVersionConflictException;
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
import java.util.ArrayList;
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
    void getDoesNotReturnAnotherTenantsRecord() throws Exception {
        UUID tenantA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID tenantB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID account = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID agreement = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        UUID line = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        LocalDate asOf = LocalDate.of(2026, 11, 1);
        MemoryStore store = new MemoryStore();
        RenewalPolicyRecordService service = new RenewalPolicyRecordService(store);
        RenewalPolicyRecord accountRow = service.append(tenantA, "service:quote-service",
                draft(RenewalPolicyScopeKind.ACCOUNT, account, asOf));
        service.append(tenantA, "service:quote-service", draft(RenewalPolicyScopeKind.AGREEMENT, agreement, asOf));
        service.append(tenantA, "service:quote-service", draft(RenewalPolicyScopeKind.LINE, line, asOf));
        var mvc = MockMvcBuilders.standaloneSetup(new RenewalPolicyController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(token(tenantB));
        try {
            String hidden = mvc.perform(get(RenewalPolicyController.PATH)
                            .param("accountId", account.toString())
                            .param("agreementId", agreement.toString())
                            .param("lineId", line.toString())
                            .param("asOf", "2026-11-01")
                            .param("tenantId", tenantA.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty())
                    .andReturn().getResponse().getContentAsString();
            assertThat(hidden).doesNotContain(accountRow.getId().toString()).doesNotContain("marker-tenant-a");
        } finally {
            SecurityContextHolder.clearContext();
        }
        SecurityContextHolder.getContext().setAuthentication(token(tenantA));
        try {
            mvc.perform(get(RenewalPolicyController.PATH)
                            .param("accountId", account.toString())
                            .param("asOf", "2026-11-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(accountRow.getId().toString()))
                    .andExpect(jsonPath("$.data[0].retentionMarker").value("marker-tenant-a"));
        } finally {
            SecurityContextHolder.clearContext();
        }
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

    private static RenewalPolicyDraft draft(RenewalPolicyScopeKind kind, UUID ref, LocalDate asOf) {
        return new RenewalPolicyDraft(kind, ref, "SAME", null, null, null, null, null,
                null, null, null, "marker-tenant-a", asOf.minusDays(1), null, null);
    }

    private static final class MemoryStore implements RenewalPolicyRecords {
        private final List<RenewalPolicyRecord> rows = new ArrayList<>();

        @Override
        public List<RenewalPolicyRecord> findScoped(UUID tenantId, RenewalPolicyScopeKind scopeKind, UUID scopeRef) {
            return rows.stream()
                    .filter(row -> tenantId.equals(row.getTenantId()))
                    .filter(row -> row.getScopeKind() == scopeKind && scopeRef.equals(row.getScopeRef()))
                    .toList();
        }

        @Override
        public RenewalPolicyRecord insert(RenewalPolicyRecord record) {
            boolean duplicate = rows.stream().anyMatch(row -> row.getTenantId().equals(record.getTenantId())
                    && row.getScopeKind() == record.getScopeKind()
                    && row.getScopeRef().equals(record.getScopeRef())
                    && row.getPolicyVersion().equals(record.getPolicyVersion()));
            if (duplicate) {
                throw new RenewalPolicyVersionConflictException("duplicate");
            }
            if (record.getId() == null) {
                record.setId(UUID.randomUUID());
            }
            rows.add(record);
            return record;
        }
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
