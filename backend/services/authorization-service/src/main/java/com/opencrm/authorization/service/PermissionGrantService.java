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

        authorizationQueryService.evictAll();
        return new PermissionDto(permission.getAction(), permission.getResource());
    }
}
