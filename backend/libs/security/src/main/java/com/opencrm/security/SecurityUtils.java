package com.opencrm.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for security operations.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Utility class
    }

    /**
     * Get the current authenticated user context.
     * <p>
     * Anonymous callers are not users: Spring Security marks an
     * {@link AnonymousAuthenticationToken} as authenticated, so without this check a
     * permitAll chain would report the principal {@code anonymousUser} as the actor
     * (ADR-016 phase 0).
     */
    public static Optional<UserContext> getCurrentUser() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(SecurityUtils::isRealUser)
                .map(SecurityUtils::extractUserContext);
    }

    /**
     * Get the current user ID or throw if not authenticated.
     */
    public static String getCurrentUserId() {
        return getCurrentUser()
                .map(UserContext::getUserId)
                .orElseThrow(() -> new SecurityException("No authenticated user found"));
    }

    /**
     * Get the current user email.
     */
    public static Optional<String> getCurrentUserEmail() {
        return getCurrentUser().map(UserContext::getEmail);
    }

    /**
     * Check if current user has a specific role.
     */
    public static boolean hasRole(String role) {
        return getCurrentUser()
                .map(user -> user.hasRole(role))
                .orElse(false);
    }

    /**
     * Check if current user is authenticated. Anonymous callers do not count.
     */
    public static boolean isAuthenticated() {
        return isRealUser(SecurityContextHolder.getContext().getAuthentication());
    }

    private static boolean isRealUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static UserContext extractUserContext(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return UserContext.builder()
                    .userId(jwt.getSubject())
                    .email(jwt.getClaimAsString("email"))
                    .displayName(jwt.getClaimAsString("name"))
                    .tenantId(jwt.getClaimAsString("tid"))
                    .roles(roles)
                    .permissions(Collections.emptySet())
                    .build();
        }

        return UserContext.builder()
                .userId(authentication.getName())
                .roles(roles)
                .permissions(Collections.emptySet())
                .build();
    }
}
