package com.opencrm.security.web;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Maps a token's app roles and scopes to Spring authorities (ADR-016 decision 7):
 * <ul>
 *   <li>each app role in the roles claim becomes {@code ROLE_<UPPER_SNAKE>}: {@code CPQ.User} to
 *       {@code ROLE_CPQ_USER}, {@code DealDesk} to {@code ROLE_DEAL_DESK}, {@code SalesVP} to {@code ROLE_SALES_VP},
 *       {@code Internal.Invoke} to {@code ROLE_INTERNAL_INVOKE};</li>
 *   <li>delegated scopes ({@code scp}/{@code scope}) become {@code SCOPE_<scope>}, Spring's default.</li>
 * </ul>
 */
public class EntraRoleAuthorities implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final String rolesClaim;
    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    public EntraRoleAuthorities(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>(scopes.convert(jwt));
        List<String> roles = jwt.getClaimAsStringList(rolesClaim);
        if (roles != null) {
            roles.stream()
                    .filter(role -> role != null && !role.isBlank())
                    .map(role -> new SimpleGrantedAuthority(authorityFor(role)))
                    .forEach(authorities::add);
        }
        return authorities;
    }

    /** The Spring authority for an app role: {@code ROLE_} plus the role in upper snake case. */
    public static String authorityFor(String appRole) {
        StringBuilder snake = new StringBuilder();
        String role = appRole.trim();
        for (int i = 0; i < role.length(); i++) {
            char c = role.charAt(i);
            if (c == '.' || c == '-' || c == ' ' || c == '_') {
                appendSeparator(snake);
                continue;
            }
            // A capital starts a new word after a lower-case letter (DealDesk) or before one (VPSales: VP_Sales).
            boolean startsWord = Character.isUpperCase(c) && i > 0
                    && (Character.isLowerCase(role.charAt(i - 1))
                        || (i + 1 < role.length() && Character.isUpperCase(role.charAt(i - 1))
                            && Character.isLowerCase(role.charAt(i + 1))));
            if (startsWord) {
                appendSeparator(snake);
            }
            snake.append(Character.toUpperCase(c));
        }
        return "ROLE_" + snake.toString().toUpperCase(Locale.ROOT);
    }

    private static void appendSeparator(StringBuilder snake) {
        if (!snake.isEmpty() && snake.charAt(snake.length() - 1) != '_') {
            snake.append('_');
        }
    }
}
