package com.opencrm.authorization.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The {@code GrantPermission} command: grant {@code action} on {@code resource} to every principal holding
 * {@code roleName}. Either may be {@code "*"} (any). Finds-or-creates the {@code Permission} row and links it to the
 * role.
 */
public record GrantPermissionCommand(
        @NotBlank String roleName,
        @NotBlank String action,
        @NotBlank String resource) {
}
