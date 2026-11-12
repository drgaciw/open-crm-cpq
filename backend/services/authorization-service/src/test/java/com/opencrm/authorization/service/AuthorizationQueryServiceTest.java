package com.opencrm.authorization.service;

import com.opencrm.authorization.config.AuthorizationCacheProperties;
import com.opencrm.authorization.dto.AuthorizationDecision;
import com.opencrm.authorization.repository.RoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * DRG-307 acceptance criteria semantics: unassigned principal denied, assigned role with matching permission
 * allowed, revoked role denied. Latency is covered separately by {@link AuthorizationQueryServiceBenchmarkTest}.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationQueryServiceTest {

    private static final String PRINCIPAL = "user:alice";

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    private AuthorizationQueryService service;

    @BeforeEach
    void setUp() {
        AuthorizationCacheProperties properties = new AuthorizationCacheProperties();
        service = new AuthorizationQueryService(roleAssignmentRepository, properties);
    }

    @Test
    @DisplayName("an unassigned principal is denied")
    void unassignedPrincipalIsDenied() {
        when(roleAssignmentRepository.findEffectivePermissionRows(PRINCIPAL)).thenReturn(List.of());

        AuthorizationDecision decision = service.authorize(PRINCIPAL, "quote:approve", "quote:123");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.principalId()).isEqualTo(PRINCIPAL);
    }

    @Test
    @DisplayName("an active role granting a matching permission is allowed")
    void assignedRoleWithMatchingPermissionIsAllowed() {
        when(roleAssignmentRepository.findEffectivePermissionRows(PRINCIPAL))
                .thenReturn(List.<Object[]>of(new Object[]{"quote:approve", "quote:123"}));

        AuthorizationDecision decision = service.authorize(PRINCIPAL, "quote:approve", "quote:123");

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    @DisplayName("a wildcard resource grant matches any resource for that action")
    void wildcardResourceMatchesAnyResource() {
        when(roleAssignmentRepository.findEffectivePermissionRows(PRINCIPAL))
                .thenReturn(List.<Object[]>of(new Object[]{"quote:approve", "*"}));

        assertThat(service.authorize(PRINCIPAL, "quote:approve", "quote:123").allowed()).isTrue();
        assertThat(service.authorize(PRINCIPAL, "quote:approve", "quote:999").allowed()).isTrue();
        assertThat(service.authorize(PRINCIPAL, "quote:reject", "quote:123").allowed()).isFalse();
    }

    @Test
    @DisplayName("a wildcard action grant matches any action on that resource")
    void wildcardActionMatchesAnyAction() {
        when(roleAssignmentRepository.findEffectivePermissionRows(PRINCIPAL))
                .thenReturn(List.<Object[]>of(new Object[]{"*", "quote:123"}));

        assertThat(service.authorize(PRINCIPAL, "quote:approve", "quote:123").allowed()).isTrue();
        assertThat(service.authorize(PRINCIPAL, "quote:delete", "quote:123").allowed()).isTrue();
        assertThat(service.authorize(PRINCIPAL, "quote:approve", "quote:999").allowed()).isFalse();
    }

    @Test
    @DisplayName("a revoked role is denied: the repository query already excludes it, and eviction forces a reload")
    void revokedRoleIsDenied() {
        when(roleAssignmentRepository.findEffectivePermissionRows(PRINCIPAL))
                .thenReturn(List.<Object[]>of(new Object[]{"quote:approve", "quote:123"}))
                .thenReturn(List.of());

        assertThat(service.authorize(PRINCIPAL, "quote:approve", "quote:123").allowed()).isTrue();

        // Revoking evicts the cached entry (RoleAssignmentService.revokeRole does this in production); the next
        // Authorize call re-reads the database, which now reflects revoked_at being set.
        service.evict(PRINCIPAL);

        assertThat(service.authorize(PRINCIPAL, "quote:approve", "quote:123").allowed()).isFalse();
    }

    @Test
    @DisplayName("a cache hit does not query the database again")
    void cacheHitSkipsTheDatabase() {
        when(roleAssignmentRepository.findEffectivePermissionRows(PRINCIPAL))
                .thenReturn(List.<Object[]>of(new Object[]{"quote:approve", "quote:123"}));

        service.authorize(PRINCIPAL, "quote:approve", "quote:123");
        service.authorize(PRINCIPAL, "quote:approve", "quote:123");
        service.authorize(PRINCIPAL, "quote:reject", "quote:123");

        verify(roleAssignmentRepository, times(1)).findEffectivePermissionRows(eq(PRINCIPAL));
        verifyNoMoreInteractions(roleAssignmentRepository);
    }
}
