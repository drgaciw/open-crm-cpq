package com.opencrm.authorization.service;

import com.opencrm.authorization.dto.GrantPermissionCommand;
import com.opencrm.authorization.dto.PermissionDto;
import com.opencrm.authorization.entity.Permission;
import com.opencrm.authorization.entity.Role;
import com.opencrm.authorization.repository.PermissionRepository;
import com.opencrm.authorization.repository.RoleRepository;
import com.opencrm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * DRG-307 / BC-19-03. {@code GrantPermission}: finds-or-creates the {@code (action, resource)} permission and links
 * it to the named role. Not an event in its own right (the story realizes only {@code RoleAssigned}); it clears the
 * whole {@link AuthorizationQueryService} cache, because precisely which cached principals a role grant affects is
 * not cheap to compute and grants are rare next to {@code Authorize} reads.
 */
@Service
@RequiredArgsConstructor
public class PermissionGrantService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuthorizationQueryService authorizationQueryService;

    @Transactional
    public PermissionDto grantPermission(GrantPermissionCommand command) {
        Role role = roleRepository.findByName(command.roleName())
                .orElseThrow(() -> new ResourceNotFoundException("Role", command.roleName()));

        Permission permission = permissionRepository.findByActionAndResource(command.action(), command.resource())
                .orElseGet(() -> permissionRepository.save(Permission.builder()
                        .action(command.action())
                        .resource(command.resource())
                        .build()));

        role.grant(permission);
        roleRepository.save(role);

        evictAllAfterCommit();
        return new PermissionDto(permission.getAction(), permission.getResource());
    }

    /**
     * Clears the Authorize cache only once the grant has committed. Evicting inside the transaction (a plain call
     * here) would run before commit: a concurrent {@code Authorize} landing in that window still reads the pre-grant
     * rows under READ COMMITTED, re-caches the old permission set, and nothing evicts it again, so the new grant
     * stays invisible until the cache TTL expires. Same ordering hazard {@link RoleAssignmentService} handles with
     * an explicit {@code TransactionTemplate}; a rolled-back grant changed nothing and evicts nothing. With no
     * transaction synchronization active (a direct call outside Spring's proxy) there is no commit to wait for, so
     * it evicts immediately.
     */
    private void evictAllAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            authorizationQueryService.evictAll();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                authorizationQueryService.evictAll();
            }
        });
    }
}
