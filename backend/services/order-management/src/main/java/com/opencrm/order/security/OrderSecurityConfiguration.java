package com.opencrm.order.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;

/** Gateway owns CSRF; domain always verifies bearer tokens, including local development. */
@Configuration
public class OrderSecurityConfiguration {
    @Bean
    com.opencrm.security.web.OpenCrmSecurityCustomizer orderSecurity(
            @Value("${oms.security.bypass:false}") boolean bypass,
            @Value("${oms.scope-id}") String scope) {
        if (bypass) throw new IllegalStateException("OMS authentication bypass is forbidden");
        return rules -> rules.requestMatchers("/actuator/prometheus").access((authentication, context) -> {
            var authenticated = authentication.get();
            boolean allowed = false;
            if (authenticated instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token
                    && token.isAuthenticated()) {
                Object permissions = token.getToken().getClaim("permissions");
                allowed = scope.equals(token.getToken().getClaim("scope_id"))
                        && permissions instanceof java.util.Collection<?> values && values.contains("oms:monitor");
            }
            return new org.springframework.security.authorization.AuthorizationDecision(allowed);
        });
    }
    @Bean
    JwtDecoder orderJwtDecoder(@Value("${oms.security.issuer-uri}") String issuer,
            @Value("${oms.security.jwk-set-uri}") String jwks,
            @Value("${oms.security.audience:order-management}") String audience) {
        if (issuer.isBlank() || jwks.isBlank() || audience.isBlank()) throw new IllegalArgumentException("OMS issuer, JWKS and audience required");
        com.opencrm.security.web.OpenCrmJwtDecoders.requireSingleTenant(issuer);
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
            jwt -> jwt.getAudience().contains(audience) ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "OMS audience required", null))));
        return decoder;
    }
}
