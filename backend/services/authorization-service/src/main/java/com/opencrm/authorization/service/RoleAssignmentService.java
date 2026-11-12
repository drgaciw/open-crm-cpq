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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * DRG-307 / BC-19-03. {@code AssignRole}, and the revoke that makes an assignment correctable (not one of the
 * story's four realized capabilities; see the PR description).
 *
 * <p>{@code assignedBy}/{@code revokedBy} come from {@link CurrentActor}, never from the request (ADR-016 decision
 * 6): the caller is a service token today (nothing retrofit yet), so the actor recorded is {@code service:<client
 * -id>} of whichever context eventually calls this.
 *
 * <p>Uses an explicit {@link TransactionTemplate} rather than {@code @Transactional} on the public methods, for two
 * reasons that are really one reason: {@link AuthorizationQueryService#evict} must run strictly after the write it
 * invalidates has committed (evicting first, as a plain in-method call under {@code @Transactional}, would run
 * before commit — a concurrent {@code Authorize} landing in that window still sees the pre-commit row under READ
 * COMMITTED, reloads the old permission set, and nothing evicts it again after commit), and
 * {@link #createAssignment} needs a transaction boundary it can actually catch a failure from, to retry the
 * "idempotent create" as a fresh read in a fresh transaction (mirrors {@code AgentTaskService.enqueue} in
 * {@code ai-service}; {@code DealBuyingRoleService.save} is the same unique-constraint-as-idempotency-guard shape,
 * reporting a conflict instead of retrying because it doesn't need the idempotent return value).
 */
@Service
public class RoleAssignmentService {

    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final RoleAssignmentMapper roleAssignmentMapper;
    private final OutboxEventPublisher outboxEventPublisher;
    private final AuthorizationQueryService authorizationQueryService;
    private final CurrentActor currentActor;
    private final TransactionTemplate transactions;

    public RoleAssignmentService(RoleRepository roleRepository, RoleAssignmentRepository roleAssignmentRepository,
                                  RoleAssignmentMapper roleAssignmentMapper, OutboxEventPublisher outboxEventPublisher,
                                  AuthorizationQueryService authorizationQueryService, CurrentActor currentActor,
                                  PlatformTransactionManager transactionManager) {
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.roleAssignmentMapper = roleAssignmentMapper;
        this.outboxEventPublisher = outboxEventPublisher;
        this.authorizationQueryService = authorizationQueryService;
        this.currentActor = currentActor;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Assigns {@code roleName} to {@code principalId}. Idempotent: re-assigning an already-active role returns the
     * existing assignment and emits no second {@code RoleAssigned}. Safe under concurrency: two callers racing to
     * assign the same (principal, role) resolve to exactly one active row — the database's unique partial index
     * (migration {@code V1_1}), not this read-then-insert check, is what actually prevents the duplicate; the
     * loser here just re-reads the winner.
     */
    public RoleAssignmentDto assignRole(AssignRoleCommand command) {
        Role role = roleRepository.findByName(command.roleName())
                .orElseThrow(() -> new ResourceNotFoundException("Role", command.roleName()));

        return roleAssignmentRepository.findActiveAssignment(command.principalId(), command.roleName())
                .map(roleAssignmentMapper::toDto)
                .orElseGet(() -> createAssignment(command.principalId(), role));
    }

    /** Revokes a principal's active hold on a role. No-op-free: a missing active assignment is a 404. */
    public RoleAssignmentDto revokeRole(RevokeRoleCommand command) {
        RoleAssignment revoked = transactions.execute(status -> {
            RoleAssignment assignment = roleAssignmentRepository
                    .findActiveAssignment(command.principalId(), command.roleName())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Active role assignment of " + command.roleName() + " for " + command.principalId()));
            assignment.setRevokedBy(currentActor.id());
            assignment.setRevokedAt(Instant.now());
            return roleAssignmentRepository.save(assignment);
        });
        // transactions.execute has returned, which means commit already happened: a concurrent Authorize can no
        // longer observe the pre-revoke row and then be told "stale" by an eviction that ran before it.
        authorizationQueryService.evict(command.principalId());
        return roleAssignmentMapper.toDto(revoked);
    }

    private RoleAssignmentDto createAssignment(String principalId, Role role) {
        Instant now = Instant.now();
        String assignedBy = currentActor.id();

        RoleAssignment created;
        try {
            created = transactions.execute(status -> {
                RoleAssignment assignment = RoleAssignment.builder()
                        .principalId(principalId)
                        .role(role)
                        .assignedBy(assignedBy)
                        .assignedAt(now)
                        .build();
                // saveAndFlush, not save: forces the unique partial index to be checked now, inside this
                // transaction attempt, so a violation surfaces here and is caught below rather than escaping at
                // some later, unrelated flush.
                RoleAssignment saved = roleAssignmentRepository.saveAndFlush(assignment);
                outboxEventPublisher.publish("RoleAssignment", saved.getUuid(), EventContractRegistry.ROLE_ASSIGNED_V1,
                        new RoleAssignedEvent(principalId, role.getName(), assignedBy, now));
                return saved;
            });
        } catch (DataIntegrityViolationException ex) {
            // Lost the race: another caller's AssignRole for the same (principalId, role) committed first. That
            // caller's own after-commit evict (below) already invalidated the cache; the idempotent outcome here
            // is simply to return what it created, read fresh (the transaction above rolled back, so nothing it
            // attempted — including the outbox write — took effect).
            RoleAssignment winner = transactions.execute(status ->
                    roleAssignmentRepository.findActiveAssignment(principalId, role.getName()).orElse(null));
            if (winner == null) {
                throw ex;
            }
            return roleAssignmentMapper.toDto(winner);
        }

        // Only reached once the insert (and its same-transaction outbox write) has actually committed.
        authorizationQueryService.evict(principalId);
        return roleAssignmentMapper.toDto(created);
    }
}
