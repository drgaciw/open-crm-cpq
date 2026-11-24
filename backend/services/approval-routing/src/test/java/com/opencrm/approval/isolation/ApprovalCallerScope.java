package com.opencrm.approval.isolation;

import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.EntraRoleAuthorities;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where an approval row's {@code tenant_id} comes from. The column value is {@link ApprovalRowTenant}.
 * Tenant is the Entra {@code tid} of the verified user, the same claim {@code QuoteTenantResolver}
 * reads through {@link SecurityUtils}. Account is the {@code account_id} claim on that token.
 * {@code X-Tenant-Id}, {@code X-Account-Id}, and body fields are recorded here only so a test
 * can show they are not the source.
 */
public final class ApprovalCallerScope {

    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String ACCOUNT_HEADER = "X-Account-Id";

    private static final ThreadLocal<Map<String, String>> CLIENT_HEADERS = new ThreadLocal<>();

    private ApprovalCallerScope() {
    }

    public static String tenantId() {
        return SecurityUtils.getCurrentUser()
                .map(UserContext::getTenantId)
                .filter(value -> !value.isBlank())
                .map(String::strip)
                .orElse(null);
    }

    public static String accountId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return null;
        }
        String claim = jwt.getToken().getClaimAsString("account_id");
        if (claim == null || claim.isBlank()) {
            return null;
        }
        return claim.strip();
    }

    /** A header the client can send. {@link #tenantId()} and {@link #accountId()} do not read it. */
    public static void clientHeader(String name, String value) {
        Map<String, String> headers = CLIENT_HEADERS.get();
        if (headers == null) {
            headers = new HashMap<>();
            CLIENT_HEADERS.set(headers);
        }
        headers.put(name, value);
    }

    public static String suppliedHeader(String name) {
        Map<String, String> headers = CLIENT_HEADERS.get();
        return headers == null ? null : headers.get(name);
    }

    public static String signIn(String name, String tenantId, String accountId) {
        Jwt.Builder jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer(TestTokens.ISSUER)
                .subject(name)
                .audience(List.of(TestTokens.AUDIENCE))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("oid", TestTokens.oidOf(name))
                .claim("azp", TestTokens.USER_CLIENT_ID)
                .claim("scp", TestTokens.USER_SCOPE)
                .claim("roles", List.of("DealDesk"));
        if (tenantId != null) {
            jwt.claim("tid", tenantId);
        }
        if (accountId != null) {
            jwt.claim("account_id", accountId);
        }
        Jwt token = jwt.build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token, new EntraRoleAuthorities("roles").convert(token)));
        return token.getClaimAsString("oid");
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
        CLIENT_HEADERS.remove();
    }
}
