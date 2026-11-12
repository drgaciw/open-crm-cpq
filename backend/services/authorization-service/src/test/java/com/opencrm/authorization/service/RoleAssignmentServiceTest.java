package com.opencrm.authorization.service;

import com.opencrm.authorization.dto.AssignRoleCommand;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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

    // No security context is set up in this unit test, so CurrentActor.id() falls back to "self" (ADR-016): a real
    // instance exercises that fallback instead of mocking it away.
    private final CurrentActor currentActor = new CurrentActor("service:authorization-service-test", false);

    private RoleAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new RoleAssignmentService(roleRepository, roleAssignmentRepository, roleAssignmentMapper,
                outboxEventPublisher, authorizationQueryService, currentActor);
    }

    @Test
    @DisplayName("AssignRole for an unknown role is a 404, and nothing is written")
    void assignRoleUnknownRoleIsNotFound() {
        when(roleRepository.findByName(ROLE_NAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignRole(new AssignRoleCommand(PRINCIPAL, ROLE_NAME)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(roleAssignmentRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("AssignRole creates a new assignment, emits RoleAssigned, and evicts the principal's cache entry")
    void assignRoleCreatesAssignmentAndEmitsRoleAssigned() {
        Role role = Role.builder().name(ROLE_NAME).build();
        when(roleRepository.findByName(ROLE_NAME)).thenReturn(Optional.of(role));
        when(roleAssignmentRepository.findActiveAssignment(PRINCIPAL, ROLE_NAME)).thenReturn(Optional.empty());
        when(roleAssignmentRepository.save(any(RoleAssignment.class))).thenAnswer(invocation -> {
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
        verify(roleAssignmentRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(anyString(), anyString(), any(), any());
        verify(authorizationQueryService, never()).evict(any());
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
}
