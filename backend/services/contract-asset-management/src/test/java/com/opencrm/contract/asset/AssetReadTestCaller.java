package com.opencrm.contract.asset;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/** Fixture caller with InstalledBase.Read, delegated scp, and a GUID tid. */
final class AssetReadTestCaller {
    static final String TENANT = "11111111-1111-4111-8111-111111111111";

    private AssetReadTestCaller() { }

    static void signIn() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .subject("installed-base-reader")
                .claim("scp", "access_as_user")
                .claim("tid", TENANT)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_INSTALLED_BASE_READ"))));
    }

    static void clear() { SecurityContextHolder.clearContext(); }
}
