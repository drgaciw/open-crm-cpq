package com.opencrm.gateway.security;

import com.opencrm.security.web.OpenCrmJwtDecoders;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import com.opencrm.security.web.OpenCrmSecurityProperties;
import com.opencrm.security.web.OpenCrmSecurityProperties.SecurityMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

/**
 * The gateway's bearer lane (ADR-016 step 3): the reactive twin of the services' decoder, with the same rules from
 * {@link OpenCrmJwtDecoders} (tenant-specific issuer, validated audience, clock skew). Agents and ai-service call the
 * gateway with a bearer token; the gateway validates it here and forwards it unchanged.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenCrmSecurityProperties.class)
public class GatewayJwtDecoderConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ReactiveJwtDecoder gatewayJwtDecoder(OpenCrmSecurityProperties security, Environment environment) {
        if (OpenCrmSecurityAutoConfiguration.mode(security, environment) == SecurityMode.DEV) {
            return token -> Mono.error(new BadJwtException("opencrm.security.mode=dev"));
        }
        return create(security.getJwt(), OpenCrmSecurityAutoConfiguration.isProduction(environment));
    }

    /** Lazy: the tenant is contacted on the first token. No issuer: every token is rejected, or startup fails in production. */
    static ReactiveJwtDecoder create(OpenCrmSecurityProperties.Jwt properties, boolean production) {
        String issuer = blankToNull(properties.getIssuerUri());
        String jwkSetUri = blankToNull(properties.getJwkSetUri());
        OpenCrmJwtDecoders.requireSingleTenant(issuer);
        OpenCrmJwtDecoders.requireAudience(properties.getAudiences());
        if (issuer == null && jwkSetUri == null) {
            if (production) {
                throw new IllegalStateException("The gateway needs opencrm.security.jwt.issuer-uri (a tenant-specific "
                        + "issuer) when the production marker is set (ADR-016)");
            }
            log.error("opencrm.security.jwt.issuer-uri is not set: the gateway rejects every bearer token (ADR-016).");
            return token -> Mono.error(new BadJwtException("No token issuer is configured (opencrm.security.jwt.issuer-uri)"));
        }
        NimbusReactiveJwtDecoder decoder = jwkSetUri != null
                ? NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build()
                : NimbusReactiveJwtDecoder.withIssuerLocation(issuer).build();
        decoder.setJwtValidator(OpenCrmJwtDecoders.validator(properties));
        return decoder;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
