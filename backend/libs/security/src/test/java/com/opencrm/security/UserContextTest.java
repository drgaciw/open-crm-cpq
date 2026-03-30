package com.opencrm.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("User Context Tests")
class UserContextTest {

    @Nested
    @DisplayName("Role Checks")
    class RoleChecks {

        @Test
        @DisplayName("hasRole should return true when user has the specific role")
        void hasRoleReturnsTrue() {
            UserContext context = UserContext.builder()
                    .roles(Set.of("USER", "MANAGER"))
                    .build();
            assertTrue(context.hasRole("USER"));
            assertTrue(context.hasRole("MANAGER"));
        }

        @Test
        @DisplayName("hasRole should return false when user does not have the specific role")
        void hasRoleReturnsFalse() {
            UserContext context = UserContext.builder()
                    .roles(Set.of("USER"))
                    .build();
            assertFalse(context.hasRole("ADMIN"));
        }

        @Test
        @DisplayName("hasRole should return false when roles are null")
        void hasRoleReturnsFalseWhenNull() {
            UserContext context = UserContext.builder().build();
            assertFalse(context.hasRole("USER"));
        }

        @Test
        @DisplayName("hasAnyRole should return true when user has at least one of the roles")
        void hasAnyRoleReturnsTrue() {
            UserContext context = UserContext.builder()
                    .roles(Set.of("USER", "MANAGER"))
                    .build();
            assertTrue(context.hasAnyRole("ADMIN", "MANAGER"));
        }

        @Test
        @DisplayName("hasAnyRole should return false when user has none of the roles")
        void hasAnyRoleReturnsFalse() {
            UserContext context = UserContext.builder()
                    .roles(Set.of("USER"))
                    .build();
            assertFalse(context.hasAnyRole("ADMIN", "MANAGER"));
        }

        @Test
        @DisplayName("hasAnyRole should return false when roles are null")
        void hasAnyRoleReturnsFalseWhenNull() {
            UserContext context = UserContext.builder().build();
            assertFalse(context.hasAnyRole("USER"));
        }

        @Test
        @DisplayName("isAdmin should return true when user is ADMIN")
        void isAdminReturnsTrueAdmin() {
            UserContext context = UserContext.builder()
                    .roles(Set.of("ADMIN"))
                    .build();
            assertTrue(context.isAdmin());
        }

        @Test
        @DisplayName("isAdmin should return true when user is ROLE_ADMIN")
        void isAdminReturnsTrueRoleAdmin() {
            UserContext context = UserContext.builder()
                    .roles(Set.of("ROLE_ADMIN"))
                    .build();
            assertTrue(context.isAdmin());
        }

        @Test
        @DisplayName("isAdmin should return false when user is not admin")
        void isAdminReturnsFalse() {
            UserContext context = UserContext.builder()
                    .roles(Set.of("USER"))
                    .build();
            assertFalse(context.isAdmin());
        }

        @Test
        @DisplayName("isAdmin should return false when roles are null")
        void isAdminReturnsFalseWhenNull() {
            UserContext context = UserContext.builder().build();
            assertFalse(context.isAdmin());
        }
    }

    @Nested
    @DisplayName("Permission Checks")
    class PermissionChecks {

        @Test
        @DisplayName("hasPermission should return true when user has the specific permission")
        void hasPermissionReturnsTrue() {
            UserContext context = UserContext.builder()
                    .permissions(Set.of("READ_DOCUMENT", "WRITE_DOCUMENT"))
                    .build();
            assertTrue(context.hasPermission("READ_DOCUMENT"));
            assertTrue(context.hasPermission("WRITE_DOCUMENT"));
        }

        @Test
        @DisplayName("hasPermission should return false when user does not have the specific permission")
        void hasPermissionReturnsFalse() {
            UserContext context = UserContext.builder()
                    .permissions(Set.of("READ_DOCUMENT"))
                    .build();
            assertFalse(context.hasPermission("DELETE_DOCUMENT"));
        }

        @Test
        @DisplayName("hasPermission should return false when permissions are null")
        void hasPermissionReturnsFalseWhenNull() {
            UserContext context = UserContext.builder().build();
            assertFalse(context.hasPermission("READ_DOCUMENT"));
        }
    }
}
