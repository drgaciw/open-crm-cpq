package com.opencrm.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;

/**
 * Security configuration for API Gateway.
 * Dev profile: all endpoints open (no auth); Spring CSRF disabled (Origin checks via
 * {@link CsrfProtectionFilter}). Production: JWT + cookie CSRF.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Dev profile: permit all requests without authentication.
     * Spring CSRF is disabled here — cookie XSRF tokens are never issued locally,
     * which blocked browser POSTs from {@code nx serve crm} (403 Access Denied).
     * Origin/Referer checks for cookie-bearing mutating requests remain in
     * {@link CsrfProtectionFilter}. Production ({@code !dev}) keeps cookie CSRF.
     */
    @Bean
    @Profile("dev")
    public SecurityWebFilterChain devSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }

    /**
     * Default (production) profile: require JWT authentication.
     */
    @Bean
    @Profile("!dev")
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
                )
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**", "/api-docs/**", "/swagger-ui/**").permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }
}
