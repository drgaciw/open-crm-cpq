package com.opencrm.approval.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for Approval Routing.
 * <p>
 * The {@code !dev} chain is unchanged. It still has no authentication mechanism, so every
 * {@code /api/v1/**} call is refused until ADR-016 phase 1 makes this service a resource
 * server; the {@code dev} chain only lets local development reach it.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Local {@code dev} profile: open HTTP so the quote BFF and {@code nx serve crm} can reach
     * this API without Entra ID. Matches crm/gateway/product/quote/pricing/pricebook
     * {@code @Profile("dev")} permitAll. Absent from every non-dev deployment.
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
