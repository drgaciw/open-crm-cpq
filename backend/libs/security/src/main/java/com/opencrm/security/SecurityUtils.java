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
 * Who is calling, from the security context (ADR-016 decision 6). Actor fields take their value from here, never from
 * the request.
 *
 * <ul>
 *   <li>{@link #getCurrentCaller()}: the verified caller, a person or a service. Anonymous requests have none.</li>
 *   <li>{@link #getCurrentUser()}: the caller only when it is a person. A service token is not a user.</li>
 * </ul>
 * A user's id is the Entra object id ({@code oid}), falling back to {@code sub}; see {@link Caller.User}.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Utility class
    }

    /**
     * The verified caller of the current request, or empty when it is anonymous or there is no request.
     * <p>
     * Anonymous callers are not callers: Spring Security marks an {@link AnonymousAuthenticationToken} as
     * authenticated, so without this check a permitAll chain would report {@code anonymousUser} as the actor
     * (ADR-016 phase 0).
     */
    public static Optional<Caller> getCurrentCaller() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isRealCaller(authentication)) {
            return Optional.empty();
        }
        Set<String> authorities = authorities(authentication);
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return Optional.of(isAppOnly(jwt)
                    ? new Caller.Service(clientIdOf(jwt), authorities)
                    : new Caller.User(userIdOf(jwt), authorities));
        }
        return Optional.of(new Caller.User(authentication.getName(), authorities));
    }

    /** The current user, when the caller is a person. */
    public static Optional<UserContext> getCurrentUser() {
        return getCurrentCaller()
                .filter(Caller.User.class::isInstance)
                .map(caller -> extractUserContext(SecurityContextHolder.getContext().getAuthentication(),
                        caller.actorId(), caller.authorities()));
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
     * Check if the current request has a verified caller, user or service. Anonymous callers do not count.
     */
    public static boolean isAuthenticated() {
        return isRealCaller(SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * Whether an Entra access token was issued to an application acting as itself (client credentials). Entra marks
     * those with the optional claim {@code idtyp=app}; without it, a token that names a client ({@code azp}, or
     * {@code appid} in v1.0) but carries no delegated scope ({@code scp}, present only in user tokens) is one too.
     */
    public static boolean isAppOnly(Jwt jwt) {
        if ("app".equalsIgnoreCase(jwt.getClaimAsString("idtyp"))) {
            return true;
        }
        return !jwt.hasClaim("scp") && (hasText(jwt.getClaimAsString("azp")) || hasText(jwt.getClaimAsString("appid")));
    }

    /** A person's id: {@code oid}, the same in every application of the tenant, else {@code sub}. */
    static String userIdOf(Jwt jwt) {
        String oid = jwt.getClaimAsString("oid");
        return hasText(oid) ? oid : jwt.getSubject();
    }

    /** The calling application's client id: {@code azp} (v2.0), {@code appid} (v1.0), else {@code sub}. */
    static String clientIdOf(Jwt jwt) {
        String azp = jwt.getClaimAsString("azp");
        if (hasText(azp)) {
            return azp;
        }
        String appid = jwt.getClaimAsString("appid");
        return hasText(appid) ? appid : jwt.getSubject();
    }

    private static boolean isRealCaller(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static Set<String> authorities(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private static UserContext extractUserContext(Authentication authentication, String userId, Set<String> roles) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return UserContext.builder()
                    .userId(userId)
                    .email(jwt.getClaimAsString("email"))
                    .displayName(jwt.getClaimAsString("name"))
                    .tenantId(jwt.getClaimAsString("tid"))
                    .roles(roles)
                    .permissions(Collections.emptySet())
                    .build();
        }
        return UserContext.builder()
                .userId(userId)
                .roles(roles)
                .permissions(Collections.emptySet())
                .build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
