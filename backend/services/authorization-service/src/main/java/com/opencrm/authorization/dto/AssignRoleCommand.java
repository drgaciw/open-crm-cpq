package com.opencrm.authorization.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The {@code AssignRole} command. {@code assignedBy} is not a field here: the service records the verified caller's
 * actor id itself (ADR-016 decision 6), never a value the request supplies.
 */
public record AssignRoleCommand(
        @NotBlank String principalId,
        @NotBlank String roleName) {
}
