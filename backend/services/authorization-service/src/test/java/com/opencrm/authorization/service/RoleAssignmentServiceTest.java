package com.opencrm.authorization.service;

import com.opencrm.authorization.config.AuthorizationCacheProperties;
import com.opencrm.authorization.dto.AssignRoleCommand;
import com.opencrm.authorization.dto.AuthorizationDecision;
import com.opencrm.authorization.dto.RevokeRoleCommand;
import com.opencrm.authorization.dto.RoleAssignmentDto;
import com.opencrm.authorization.entity.Role;
import com.opencrm.authorization.entity.RoleAssignment;
import com.opencrm.authorization.mapper.RoleAssignmentMapper;
import com.opencrm.authorization.repository.RoleAssignmentRepository;
import com.opencrm.authorization.repository.RoleRepository;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.RoleAssignedEvent;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code transactionManager} is a plain Mockito mock: {@code getTransaction}/{@code commit}/{@code rollback} are
 * no-ops that still let {@link org.springframework.transaction.support.TransactionTemplate} drive the callback and
 * call {@code commit}/{@code rollback} at the right moments, which is exactly what the ordering assertions below
 * (commit, then cache eviction) depend on — a real transaction manager and database aren't needed to prove the
 * ordering bug is fixed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleAssignmentServiceTest {

    private static final String PRINCIPAL = "user:alice";
    private static final String ROLE_NAME = "DealDesk";

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;
    @Mock
    private RoleAssignmentMapper roleAssignmentMapper;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private AuthorizationQueryService authorizationQueryService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    // No security context is set up in this unit test, so CurrentActor.id() falls back to "self" (ADR-016): a real
    // instance exercises that fallback instead of mocking it away.
    private final CurrentActor currentActor = new CurrentActor("service:authorization-service-test", false);

    private RoleAssignmentService service;

    @BeforeEach
    void setUp() {
        // TransactionTemplate.execute() calls commit(status)/rollback(status) with whatever getTransaction()
        // returned; stubbing a real (mock) TransactionStatus here, instead of leaving it null, is what lets the
        // ordering assertions below match "commit(any(TransactionStatus.class))" at all.
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new RoleAssignmentService(roleRepository, roleAssignmentRepository, roleAssignmentMapper,
                outboxEventPublisher, authorizationQueryService, currentActor, transactionManager);
    }

    @Test
    @DisplayName("AssignRole for an unknown role is a 404, and nothing is written")
    void assignRoleUnknownRoleIsNotFound() {
        when(roleRepository.findByName(ROLE_NAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignRole(new AssignRoleCommand(PRINCIPAL, ROLE_NAME)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(roleAssignmentRepository, never()).saveAndFlush(any());
        verify(outboxEventPublisher, never()).publish(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("AssignRole creates a new assignment and emits RoleAssigned")
    void assignRoleCreatesAssignmentAndEmitsRoleAssigned() {
        Role role = Role.builder().name(ROLE_NAME).build();
        when(roleRepository.findByName(ROLE_NAME)).thenReturn(Optional.of(role));
        when(roleAssignmentRepository.findActiveAssignment(PRINCIPAL, ROLE_NAME)).thenReturn(Optional.empty());
        when(roleAssignmentRepository.saveAndFlush(any(RoleAssignment.class))).thenAnswer(invocation -> {
            RoleAssignment assignment = invocation.getArgument(0);
            assignment.setUuid("assignment-uuid");
            return assignment;
        });
        RoleAssignmentDto expectedDto = new RoleAssignmentDto("assignment-uuid", PRINCIPAL, ROLE_NAME,
                "service:authorization-service-test", null, null, null);
        when(roleAssignmentMapper.toDto(any(RoleAssignment.class))).thenReturn(expectedDto);

        RoleAssignmentDto result = service.assignRole(new AssignRoleCommand(PRINCIPAL, ROLE_NAME));

        assertThat(result).isEqualTo(expectedDto);

        ArgumentCaptor<RoleAssignedEvent> payloadCaptor = ArgumentCaptor.forClass(RoleAssignedEvent.class);
        verify(outboxEventPublisher).publish(eq("RoleAssignment"), eq("assignment-uuid"),
                eq(EventContractRegistry.ROLE_ASSIGNED_V1), payloadCaptor.capture());
        RoleAssignedEvent payload = payloadCaptor.getValue();
        assertThat(payload.getPrincipalId()).isEqualTo(PRINCIPAL);
        assertThat(payload.getRoleName()).isEqualTo(ROLE_NAME);
        assertThat(payload.getAssignedBy()).isEqualTo("service:authorization-service-test");
        assertThat(payload.getAssignedAt()).isNotNull();

        verify(authorizationQueryService).evict(PRINCIPAL);
    }

    @Test
    @DisplayName("P1 regression: the cache is evicted strictly after the assigning transaction commits, never before")
    void assignRoleEvictsOnlyAfterTheTransactionCommits() {
        Role role = Role.builder().name(ROLE_NAME).build();
        when(roleRepository.findByName(ROLE_NAME)).thenReturn(Optional.of(role));
        when(roleAssignmentRepository.findActiveAssignment(PRINCIPAL, ROLE_NAME)).thenReturn(Optional.empty());
        when(roleAssignmentRepository.saveAndFlush(any(RoleAssignment.class))).thenAnswer(invocation -> {
            RoleAssignment assignment = invocation.getArgument(0);
            assignment.setUuid("assignment-uuid");
            return assignment;
        });
        when(roleAssignmentMapper.toDto(any(RoleAssignment.class))).thenReturn(
                new RoleAssignmentDto("assignment-uuid", PRINCIPAL, ROLE_NAME, "x", null, null, null));

        service.assignRole(new AssignRoleCommand(PRINCIPAL, ROLE_NAME));

        // Before the fix, evict(principalId) ran inline inside the @Transactional method body, i.e. before
        // TransactionTemplate/the transaction manager ever calls commit(). This fails on the old code.
        InOrder order = inOrder(transactionManager, authorizationQueryService);
        order.verify(transactionManager).commit(any(TransactionStatus.class));
        order.verify(authorizationQueryService).evict(PRINCIPAL);
    }

    @Test
    @DisplayName("AssignRole is idempotent: an already-active hold is returned, with no new row and no second event")
    void assignRoleIsIdempotentForAnActiveHold() {
        Role role = Role.builder().name(ROLE_NAME).build();
        RoleAssignment existing = RoleAssignment.builder().principalId(PRINCIPAL).role(role).build();
        RoleAssignmentDto existingDto = new RoleAssignmentDto("existing-uuid", PRINCIPAL, ROLE_NAME, "someone", null,
                null, null);
        when(roleRepository.findByName(ROLE_NAME)).thenReturn(Optional.of(role));
        when(roleAssignmentRepository.findActiveAssignment(PRINCIPAL, ROLE_NAME)).thenReturn(Optional.of(existing));
        when(roleAssignmentMapper.toDto(existing)).thenReturn(existingDto);

        RoleAssignmentDto result = service.assignRole(new AssignRoleCommand(PRINCIPAL, ROLE_NAME));

        assertThat(result).isEqualTo(existingDto);
        verify(roleAssignmentRepository, never()).saveAndFlush(any());
        verify(outboxEventPublisher, never()).publish(anyString(), anyString(), any(), any());
        verify(authorizationQueryService, never()).evict(any());
    }

    @Test
    @DisplayName("P2 regression: two concurrent AssignRole calls for the same (principal, role) resolve to one "
            + "active assignment, without NonUniqueResultException/DataIntegrityViolationException escaping")
    void concurrentAssignRoleCallsResolveToOneActiveAssignmentWithoutThrowing() {
        Role role = Role.builder().name(ROLE_NAME).build();
        when(roleRepository.findByName(ROLE_NAME)).thenReturn(Optional.of(role));

        RoleAssignment winner = RoleAssignment.builder().principalId(PRINCIPAL).role(role).build();
        winner.setUuid("winner-uuid");
        RoleAssignmentDto winnerDto = new RoleAssignmentDto("winner-uuid", PRINCIPAL, ROLE_NAME, "the-other-caller",
                null, null, null);

        // This call's own read-before-write check sees nothing yet (the race): both callers reach the insert.
        // The retry re-read, after this call's own insert is rejected, finds the row the other caller committed
        // first — exactly what the database's unique partial index (V1_1) makes possible instead of a 500.
        when(roleAssignmentRepository.findActiveAssignment(PRINCIPAL, ROLE_NAME))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(roleAssignmentRepository.saveAndFlush(any(RoleAssignment.class)))
                .thenThrow(new DataIntegrityViolationException("uk_role_assignments_principal_role_active"));
        when(roleAssignmentMapper.toDto(winner)).thenReturn(winnerDto);

        RoleAssignmentDto result = service.assignRole(new AssignRoleCommand(PRINCIPAL, ROLE_NAME));

        assertThat(result).isEqualTo(winnerDto);
        // The losing attempt's own transaction rolled back: its outbox write and cache eviction never took effect.
        verify(outboxEventPublisher, never()).publish(anyString(), anyString(), any(), any());
        verify(authorizationQueryService, never()).evict(any());
        verify(transactionManager).rollback(any(TransactionStatus.class));
    }

    @Test
    @DisplayName("a genuine database failure (no winner to fall back to) still propagates")
    void assignRoleReportsAConflictItCannotResolve() {
        Role role = Role.builder().name(ROLE_NAME).build();
        when(roleRepository.findByName(ROLE_NAME)).thenReturn(Optional.of(role));
        DataIntegrityViolationException failure = new DataIntegrityViolationException("some other constraint");
        when(roleAssignmentRepository.findActiveAssignment(PRINCIPAL, ROLE_NAME))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(roleAssignmentRepository.saveAndFlush(any(RoleAssignment.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.assignRole(new AssignRoleCommand(PRINCIPAL, ROLE_NAME)))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("revoking with no active assignment is a 404")
    void revokeRoleWithNoActiveAssignmentIsNotFound() {
        when(roleAssignmentRepository.findActiveAssignment(PRINCIPAL, ROLE_NAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeRole(new RevokeRoleCommand(PRINCIPAL, ROLE_NAME)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("revoking an active assignment records who and when, and evicts the principal's cache entry")
    void revokeRoleRevokesAndEvicts() {
        Role role = Role.builder().name(ROLE_NAME).build();
        RoleAssignment existing = RoleAssignment.builder().principalId(PRINCIPAL).role(role).build();
        when(roleAssignmentRepository.findActiveAssignment(PRINCIPAL, ROLE_NAME)).thenReturn(Optional.of(existing));
        when(roleAssignmentRepository.save(existing)).thenReturn(existing);
        RoleAssignmentDto dto = new RoleAssignmentDto("uuid", PRINCIPAL, ROLE_NAME, "someone", null,
                "service:authorization-service-test", null);
        when(roleAssignmentMapper.toDto(existing)).thenReturn(dto);

        RoleAssignmentDto result = service.revokeRole(new RevokeRoleCommand(PRINCIPAL, ROLE_NAME));

        assertThat(result).isEqualTo(dto);
        assertThat(existing.getRevokedBy()).isEqualTo("service:authorization-service-test");
        assertThat(existing.getRevokedAt()).isNotNull();
        verify(authorizationQueryService).evict(PRINCIPAL);
    }

    @Test
    @DisplayName("P1 regression: revoke evicts strictly after its transaction commits, so Authorize right after a "
            + "revoke is never stale for the cache's TTL")
    void revokeRoleEvictsOnlyAfterTheTransactionCommits() {
        Role role = Role.builder().name(ROLE_NAME).build();
        RoleAssignment existing = RoleAssignment.builder().principalId(PRINCIPAL).role(role).build();
        when(roleAssignmentRepository.findActiveAssignment(PRINCIPAL, ROLE_NAME)).thenReturn(Optional.of(existing));
        when(roleAssignmentRepository.save(existing)).thenReturn(existing);
        when(roleAssignmentMapper.toDto(existing)).thenReturn(
                new RoleAssignmentDto("uuid", PRINCIPAL, ROLE_NAME, "x", null, "x", null));

        service.revokeRole(new RevokeRoleCommand(PRINCIPAL, ROLE_NAME));

        InOrder order = inOrder(transactionManager, authorizationQueryService);
        order.verify(transactionManager).commit(any(TransactionStatus.class));
        order.verify(authorizationQueryService).evict(PRINCIPAL);
    }

    @Test
    @DisplayName("end-to-end: a revoke that commits is reflected by Authorize right after, not stale for the "
            + "cache's TTL")
    void revokeIsReflectedByAuthorizeImmediatelyAfterwards() {
        // A real AuthorizationQueryService (not a mock) wired to the same repository mock as the
        // RoleAssignmentService under test, so evict() actually does something observable: this is the thing
        // the P1 fix (evict strictly after commit) is for.
        AuthorizationQueryService realAuthorizationQueryService =
                new AuthorizationQueryService(roleAssignmentRepository, new AuthorizationCacheProperties());
        RoleAssignmentService serviceUnderRealCache = new RoleAssignmentService(roleRepository,
                roleAssignmentRepository, roleAssignmentMapper, outboxEventPublisher, realAuthorizationQueryService,
                currentActor, transactionManager);

        Role role = Role.builder().name(ROLE_NAME).build();
        RoleAssignment existing = RoleAssignment.builder().principalId(PRINCIPAL).role(role).build();
        when(roleAssignmentRepository.findActiveAssignment(PRINCIPAL, ROLE_NAME)).thenReturn(Optional.of(existing));
        when(roleAssignmentRepository.save(existing)).thenReturn(existing);
        when(roleAssignmentMapper.toDto(existing)).thenReturn(
                new RoleAssignmentDto("uuid", PRINCIPAL, ROLE_NAME, "x", null, "x", null));
        // Before the revoke: the role grants quote:approve on quote:123.
        when(roleAssignmentRepository.findEffectivePermissionRows(PRINCIPAL))
                .thenReturn(List.<Object[]>of(new Object[]{"quote:approve", "quote:123"}))
                // After the revoke commits: the database no longer has an active assignment for this principal.
                .thenReturn(List.of());

        AuthorizationDecision before = realAuthorizationQueryService.authorize(PRINCIPAL, "quote:approve",
                "quote:123");
        assertThat(before.allowed()).as("primed/cached before the revoke").isTrue();

        serviceUnderRealCache.revokeRole(new RevokeRoleCommand(PRINCIPAL, ROLE_NAME));

        AuthorizationDecision after = realAuthorizationQueryService.authorize(PRINCIPAL, "quote:approve",
                "quote:123");
        assertThat(after.allowed()).as("not stale: evicted by the revoke, reloaded from the post-revoke database")
                .isFalse();
    }
}
