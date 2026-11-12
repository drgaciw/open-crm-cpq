package com.opencrm.authorization.dto;

/** API-boundary view of a {@code Permission} (ADR-006: the entity never crosses the boundary). */
public record PermissionDto(String action, String resource) {
}
