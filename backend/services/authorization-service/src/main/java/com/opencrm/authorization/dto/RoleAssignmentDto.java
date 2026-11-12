package com.opencrm.authorization.dto;

import java.time.Instant;

/** API-boundary view of a {@code RoleAssignment} (ADR-006: the entity never crosses the boundary). */
public record RoleAssignmentDto(
        String uuid,
        String principalId,
        String roleName,
        String assignedBy,
        Instant assignedAt,
        String revokedBy,
        Instant revokedAt) {
}
