package com.opencrm.authorization.service;

import com.opencrm.authorization.dto.GrantPermissionCommand;
import com.opencrm.authorization.dto.PermissionDto;
import com.opencrm.authorization.entity.Permission;
import com.opencrm.authorization.entity.Role;
import com.opencrm.authorization.repository.PermissionRepository;
import com.opencrm.authorization.repository.RoleRepository;
import com.opencrm.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionGrantServiceTest {

    private static final String ROLE_NAME = "PricingAdmin";
    private static final String ACTION = "pricebook:publish";
    private static final String RESOURCE = "pricebook:*";

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private AuthorizationQueryService authorizationQueryService;

    private PermissionGrantService service;

    @BeforeEach
    void setUp() {
        service = new PermissionGrantService(roleRepository, permissionRepository, authorizationQueryService);
    }

    @Test
    @DisplayName("GrantPermission for an unknown role is a 404")
    void grantPermissionUnknownRoleIsNotFound() {
        when(roleRepository.findByName(ROLE_NAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grantPermission(new GrantPermissionCommand(ROLE_NAME, ACTION, RESOURCE)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(authorizationQueryService, never()).evictAll();
    }

    @Test
    @DisplayName("GrantPermission creates a new permission, links it to the role, and clears the whole cache")
    void grantPermissionCreatesAndLinksANewPermission() {
        Role role = Role.builder().name(ROLE_NAME).build();
        when(roleRepository.findByName(ROLE_NAME)).thenReturn(Optional.of(role));
        when(permissionRepository.findByActionAndResource(ACTION, RESOURCE)).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PermissionDto result = service.grantPermission(new GrantPermissionCommand(ROLE_NAME, ACTION, RESOURCE));

        assertThat(result).isEqualTo(new PermissionDto(ACTION, RESOURCE));
        assertThat(role.getPermissions()).extracting(Permission::getAction, Permission::getResource)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ACTION, RESOURCE));
        verify(roleRepository).save(role);
        verify(authorizationQueryService).evictAll();
    }

    @Test
    @DisplayName("GrantPermission reuses an existing (action, resource) permission instead of duplicating it")
    void grantPermissionReusesAnExistingPermission() {
        Role role = Role.builder().name(ROLE_NAME).build();
        Permission existing = Permission.builder().action(ACTION).resource(RESOURCE).build();
        when(roleRepository.findByName(ROLE_NAME)).thenReturn(Optional.of(role));
        when(permissionRepository.findByActionAndResource(ACTION, RESOURCE)).thenReturn(Optional.of(existing));

        service.grantPermission(new GrantPermissionCommand(ROLE_NAME, ACTION, RESOURCE));

        verify(permissionRepository, never()).save(any());
        assertThat(role.getPermissions()).containsExactly(existing);
    }
}
