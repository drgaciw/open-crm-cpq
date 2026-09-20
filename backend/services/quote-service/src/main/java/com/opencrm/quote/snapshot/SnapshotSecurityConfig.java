package com.opencrm.quote.snapshot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.web.SecurityFilterChain;

/** Applies even in dev: missing issuer configuration denies every snapshot request. */
@Configuration
public class SnapshotSecurityConfig {
    @Bean @Order(1)
    SecurityFilterChain snapshotSecurityFilterChain(HttpSecurity http,
            @Value("${oms.quote.snapshot-issuer-uri:}") String issuer) throws Exception {
        http.securityMatcher("/internal/oms/**").csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (issuer.isBlank()) http.authorizeHttpRequests(a -> a.anyRequest().denyAll());
        else http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(JwtDecoders.fromIssuerLocation(issuer))));
        return http.build();
    }
}
