package com.opencrm.order.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** Authorization derives only from verified JWT claims, never request bodies or forwarding headers. */
@Component
public class OrderAccess {
    private final String deploymentScope;
    public OrderAccess(@Value("${oms.scope-id:}") String deploymentScope) {
        if (deploymentScope == null || deploymentScope.isBlank()) {
            throw new IllegalArgumentException("oms.scope-id is required");
        }
        this.deploymentScope = deploymentScope;
    }
    public void require(String permission, String scopeId, String customerId) {
        var token = token();
        if (!strings(token, "permissions").contains(permission)) {
            throw denied();
        }
        if (!scopeId().equals(scopeId) || !customerIds().contains(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order unavailable");
        }
    }
    public String actor() {
        String subject = token().getSubject();
        if (subject == null || subject.isBlank()) throw denied();
        return subject;
    }
    public String scopeId() {
        Object claim = token().getClaim("scope_id");
        if (!(claim instanceof String scope) || !deploymentScope.equals(scope)) throw denied();
        return scope;
    }
    public Set<String> customerIds() {
        scopeId();
        Set<String> ids = strings(token(), "account_ids");
        if (ids.isEmpty() || ids.contains("*")) throw denied();
        return ids;
    }
    private Jwt token() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwt) || !auth.isAuthenticated()) throw denied();
        return jwt.getToken();
    }
    private Set<String> strings(Jwt token, String name) {
        Object raw = token.getClaim(name);
        if (!(raw instanceof Collection<?> list) || list.stream().anyMatch(v -> !(v instanceof String s) || s.isBlank())) {
            throw denied();
        }
        return list.stream().map(String.class::cast).collect(Collectors.toUnmodifiableSet());
    }
    private AccessDeniedException denied() { return new AccessDeniedException("OMS permission denied"); }
}
