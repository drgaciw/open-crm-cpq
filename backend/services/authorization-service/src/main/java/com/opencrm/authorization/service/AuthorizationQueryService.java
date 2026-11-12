package com.opencrm.authorization.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.opencrm.authorization.config.AuthorizationCacheProperties;
import com.opencrm.authorization.dto.AuthorizationDecision;
import com.opencrm.authorization.repository.RoleAssignmentRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * DRG-307 / BC-19-03. {@code Authorize(principal, action, resource)}: the one place a bounded context asks whether a
 * principal (a person's actor id, or {@code service:<client-id>}) may perform {@code action} on {@code resource}.
 *
 * <p>A principal's effective permission set — the union of every active role's permissions — is cached by Caffeine,
 * keyed by principal. A cache hit is a {@code ConcurrentHashMap} read plus an in-memory set scan: comfortably under
 * the story's 10ms-from-cache requirement (see {@code AuthorizationQueryServiceBenchmarkTest}). A miss costs one
 * database round trip ({@link RoleAssignmentRepository#findEffectivePermissionRows}) and is not covered by that
 * requirement.
 *
 * <p>{@link RoleAssignmentService} evicts a single principal's entry on {@code AssignRole}/revoke;
 * {@link PermissionGrantService} invalidates the whole cache on {@code GrantPermission}, because a permission grant
 * is rare and its blast radius (every principal holding the role) is not cheap to compute precisely.
 */
@Service
public class AuthorizationQueryService {

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final Cache<String, Set<PermissionKey>> permissionsByPrincipal;

    public AuthorizationQueryService(RoleAssignmentRepository roleAssignmentRepository,
                                      AuthorizationCacheProperties cacheProperties) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.permissionsByPrincipal = Caffeine.newBuilder()
                .maximumSize(cacheProperties.getMaximumSize())
                .expireAfterWrite(cacheProperties.getExpireAfterWrite())
                .recordStats()
                .build();
    }

    /** The Authorize query. Allowed only when an active role of the principal grants a matching permission. */
    public AuthorizationDecision authorize(String principalId, String action, String resource) {
        boolean allowed = effectivePermissions(principalId).stream()
                .anyMatch(permission -> permission.matches(action, resource));
        return new AuthorizationDecision(principalId, action, resource, allowed);
    }

    /** The cache-warm (or cache-filling) set of (action, resource) pairs the principal currently holds. */
    Set<PermissionKey> effectivePermissions(String principalId) {
        return permissionsByPrincipal.get(principalId, this::loadFromDatabase);
    }

    /** Evicts one principal's cached entry, e.g. after {@code AssignRole} or a revoke. */
    void evict(String principalId) {
        permissionsByPrincipal.invalidate(principalId);
    }

    /** Clears every cached entry, e.g. after {@code GrantPermission}, whose blast radius is not cheap to target. */
    void evictAll() {
        permissionsByPrincipal.invalidateAll();
    }

    private Set<PermissionKey> loadFromDatabase(String principalId) {
        return roleAssignmentRepository.findEffectivePermissionRows(principalId).stream()
                .map(row -> new PermissionKey((String) row[0], (String) row[1]))
                .collect(Collectors.toUnmodifiableSet());
    }
}
