package com.opencrm.contract.asset;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

final class AssetReadTestCaller {
    private AssetReadTestCaller() { }
    static void signIn() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "installed-base-reader", "n/a", List.of(new SimpleGrantedAuthority("ROLE_INSTALLED_BASE_READ"))));
    }
    static void clear() { SecurityContextHolder.clearContext(); }
}
