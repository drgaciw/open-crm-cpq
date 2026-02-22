package com.opencrm.security;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * Represents the current authenticated user context.
 */
@Data
@Builder
public class UserContext {

    private String userId;
    private String email;
    private String displayName;
    private String tenantId;
    private Set<String> roles;
    private Set<String> permissions;

    /**
     * Check if user has a specific role.
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /**
     * Check if user has a specific permission.
     */
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    /**
     * Check if user has any of the specified roles.
     */
    public boolean hasAnyRole(String... roles) {
        if (this.roles == null) return false;
        for (String role : roles) {
            if (this.roles.contains(role)) return true;
        }
        return false;
    }

    /**
     * Check if user is an admin.
     */
    public boolean isAdmin() {
        return hasRole("ADMIN") || hasRole("ROLE_ADMIN");
    }
}
