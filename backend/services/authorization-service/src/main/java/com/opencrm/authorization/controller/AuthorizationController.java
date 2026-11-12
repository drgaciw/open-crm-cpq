package com.opencrm.authorization.controller;

import com.opencrm.authorization.dto.AssignRoleCommand;
import com.opencrm.authorization.dto.AuthorizationDecision;
import com.opencrm.authorization.dto.GrantPermissionCommand;
import com.opencrm.authorization.dto.PermissionDto;
import com.opencrm.authorization.dto.RevokeRoleCommand;
import com.opencrm.authorization.dto.RoleAssignmentDto;
import com.opencrm.authorization.service.AuthorizationQueryService;
import com.opencrm.authorization.service.PermissionGrantService;
import com.opencrm.authorization.service.RoleAssignmentService;
import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * DRG-307 / BC-19-03. Role and permission model, service-to-service only: every path is under {@code /internal/**},
 * which the {@code libs/security} baseline already restricts to a caller holding the {@code Internal.Invoke} service
 * token (ADR-016 decision 4) — no security code of this service's own is needed for that.
 *
 * <p>Additive: this story builds the capability; it does not retrofit another service to call it. The first real
 * caller attaches its own outbound service-token credential (ADR-016 decision 5, {@code libs/security}'s
 * {@code OutboundAuth}), the same way {@code quote-service} and {@code pricing-service} already call other
 * {@code /internal/**} endpoints.
 */
@RestController
@RequestMapping("/internal/v1/authorization")
@RequiredArgsConstructor
@Tag(name = "Authorization", description = "Role/permission model: AssignRole, GrantPermission, Authorize")
public class AuthorizationController {

    private final RoleAssignmentService roleAssignmentService;
    private final PermissionGrantService permissionGrantService;
    private final AuthorizationQueryService authorizationQueryService;

    @PostMapping("/role-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "AssignRole: give a principal a role. Idempotent; emits RoleAssigned once per new hold.")
    public ApiResponse<RoleAssignmentDto> assignRole(@Valid @RequestBody AssignRoleCommand command) {
        return ApiResponse.success(roleAssignmentService.assignRole(command), "Role assigned");
    }

    @DeleteMapping("/role-assignments")
    @Operation(summary = "Revoke a principal's active hold on a role")
    public ApiResponse<RoleAssignmentDto> revokeRole(@Valid @RequestBody RevokeRoleCommand command) {
        return ApiResponse.success(roleAssignmentService.revokeRole(command), "Role revoked");
    }

    @PostMapping("/permission-grants")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "GrantPermission: grant an action on a resource to every principal holding a role")
    public ApiResponse<PermissionDto> grantPermission(@Valid @RequestBody GrantPermissionCommand command) {
        return ApiResponse.success(permissionGrantService.grantPermission(command), "Permission granted");
    }

    @GetMapping("/authorize")
    @Operation(summary = "Authorize(principal, action, resource): evaluated from a Caffeine cache in under 10ms warm")
    public ApiResponse<AuthorizationDecision> authorize(@RequestParam String principalId,
                                                          @RequestParam String action,
                                                          @RequestParam String resource) {
        return ApiResponse.success(authorizationQueryService.authorize(principalId, action, resource));
    }
}
