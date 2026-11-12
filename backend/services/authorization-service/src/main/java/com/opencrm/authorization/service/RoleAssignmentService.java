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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * DRG-307 / BC-19-03. {@code AssignRole}, and the revoke that makes an assignment correctable (not one of the
 * story's four realized capabilities; see the PR description).
 *
 * <p>{@code assignedBy}/{@code revokedBy} come from {@link CurrentActor}, never from the request (ADR-016 decision
 * 6): the caller is a service token today (nothing retrofit yet), so the actor recorded is {@code service:<client
 * -id>} of whichever context eventually calls this.
 */
@Service
@RequiredArgsConstructor
public class RoleAssignmentService {

    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final RoleAssignmentMapper roleAssignmentMapper;
    private final OutboxEventPublisher outboxEventPublisher;
    private final AuthorizationQueryService authorizationQueryService;
    private final CurrentActor currentActor;

    /**
     * Assigns {@code roleName} to {@code principalId}. Idempotent: re-assigning an already-active role returns the
     * existing assignment and emits no second {@code RoleAssigned}.
     */
    @Transactional
    public RoleAssignmentDto assignRole(AssignRoleCommand command) {
        Role role = roleRepository.findByName(command.roleName())
                .orElseThrow(() -> new ResourceNotFoundException("Role", command.roleName()));

        return roleAssignmentRepository.findActiveAssignment(command.principalId(), command.roleName())
                .map(roleAssignmentMapper::toDto)
                .orElseGet(() -> createAssignment(command.principalId(), role));
    }

    /** Revokes a principal's active hold on a role. No-op-free: a missing active assignment is a 404. */
    @Transactional
    public RoleAssignmentDto revokeRole(RevokeRoleCommand command) {
        RoleAssignment assignment = roleAssignmentRepository
                .findActiveAssignment(command.principalId(), command.roleName())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active role assignment of " + command.roleName() + " for " + command.principalId()));
        assignment.setRevokedBy(currentActor.id());
        assignment.setRevokedAt(Instant.now());
        RoleAssignment saved = roleAssignmentRepository.save(assignment);
        authorizationQueryService.evict(command.principalId());
        return roleAssignmentMapper.toDto(saved);
    }

    private RoleAssignmentDto createAssignment(String principalId, Role role) {
        Instant now = Instant.now();
        String assignedBy = currentActor.id();

        RoleAssignment assignment = RoleAssignment.builder()
                .principalId(principalId)
                .role(role)
                .assignedBy(assignedBy)
                .assignedAt(now)
                .build();
        RoleAssignment saved = roleAssignmentRepository.save(assignment);

        outboxEventPublisher.publish("RoleAssignment", saved.getUuid(), EventContractRegistry.ROLE_ASSIGNED_V1,
                new RoleAssignedEvent(principalId, role.getName(), assignedBy, now));

        authorizationQueryService.evict(principalId);
        return roleAssignmentMapper.toDto(saved);
    }
}
