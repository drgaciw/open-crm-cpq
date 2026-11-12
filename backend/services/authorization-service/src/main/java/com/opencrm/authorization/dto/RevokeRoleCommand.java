package com.opencrm.authorization.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Revokes a previously assigned role. Not one of the story's four realized capabilities (AssignRole,
 * GrantPermission, Authorize, RoleAssigned) but needed to make a role assignment correctable; see the PR
 * description.
 */
public record RevokeRoleCommand(
        @NotBlank String principalId,
        @NotBlank String roleName) {
}
