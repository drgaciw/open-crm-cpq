package com.opencrm.quote.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for Quote Service.
 *
 * Local {@code dev} profile: open HTTP so {@code nx serve crm} can reach quote
 * APIs without Azure AD. Matches CRM/gateway {@code @Profile("dev")} permitAll.
 * Production ({@code !dev}) still requires authentication. CSRF stays disabled
 * at the domain (ADR-004 — gateway Cookie CSRF is the edge).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Local {@code dev} profile: open HTTP so {@code nx serve crm} can reach quote
     * APIs without Azure AD. Matches CRM {@code @Profile("dev")} permitAll. Not used in
     * production ({@code !dev} still requires authentication).
     */
    @Bean
    @Profile("dev")
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Profile("!dev")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated()
                )
                .build();
    }
}
