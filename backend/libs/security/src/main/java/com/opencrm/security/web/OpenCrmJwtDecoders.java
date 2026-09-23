package com.opencrm.security.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Builds the {@link JwtDecoder} of {@code jwt} mode and enforces what ADR-016 decision 1 requires of it:
 * <ul>
 *   <li>the issuer is tenant-specific: {@code common}, {@code organizations} and {@code consumers} are refused;</li>
 *   <li>the audience is validated: a token must name one of {@code opencrm.security.jwt.audiences};</li>
 *   <li>{@code exp} and {@code nbf} are checked with {@code clock-skew}.</li>
 * </ul>
 * The decoder is lazy: the identity provider is contacted on the first token, not at startup.
 *
 * <p>With no issuer and no JWKS configured, the decoder rejects every token and a startup ERROR says so. That is how
 * {@code jwt} mode behaved before ADR-016 (every request refused), so an environment without an identity provider is no
 * worse off. With the production marker set, a missing issuer is a startup failure instead.
 */
@Slf4j
public final class OpenCrmJwtDecoders {

    private static final List<String> MULTI_TENANT_SEGMENTS = List.of("/common/", "/organizations/", "/consumers/");

    private OpenCrmJwtDecoders() {
    }

    /** The decoder for {@code properties}; see the class comment for the rules. */
    public static JwtDecoder create(OpenCrmSecurityProperties.Jwt properties, boolean production) {
        String issuer = blankToNull(properties.getIssuerUri());
        String jwkSetUri = blankToNull(properties.getJwkSetUri());
        requireSingleTenant(issuer);
        requireAudience(properties.getAudiences());
        if (issuer == null && jwkSetUri == null) {
            if (production) {
                throw new IllegalStateException("opencrm.security.mode=jwt with the production marker set needs "
                        + "opencrm.security.jwt.issuer-uri (a tenant-specific issuer) (ADR-016)");
            }
            log.error("opencrm.security.jwt.issuer-uri is not set: every bearer token will be rejected. "
                    + "Configure the tenant issuer, or use opencrm.security.mode=dev locally (ADR-016).");
            return token -> {
                throw new BadJwtException("No token issuer is configured (opencrm.security.jwt.issuer-uri)");
            };
        }
        OAuth2TokenValidator<Jwt> validator = validator(properties);
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = jwkSetUri != null
                    ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
                    : NimbusJwtDecoder.withIssuerLocation(issuer).build();
            decoder.setJwtValidator(validator);
            return decoder;
        });
    }

    /** Timestamp (with clock skew), issuer when configured, and audience. Public so tests use the same rules. */
    public static OAuth2TokenValidator<Jwt> validator(OpenCrmSecurityProperties.Jwt properties) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator(properties.getClockSkew()));
        String issuer = blankToNull(properties.getIssuerUri());
        if (issuer != null) {
            validators.add(new JwtIssuerValidator(issuer));
        }
        List<String> audiences = List.copyOf(properties.getAudiences());
        validators.add(new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.stream().anyMatch(audiences::contains)));
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    static void requireSingleTenant(String issuer) {
        if (issuer == null) {
            return;
        }
        String normalized = issuer.toLowerCase(Locale.ROOT) + "/";
        for (String segment : MULTI_TENANT_SEGMENTS) {
            if (normalized.contains(segment)) {
                throw new IllegalStateException("opencrm.security.jwt.issuer-uri must be tenant-specific, not "
                        + issuer + " (ADR-016 decision 1)");
            }
        }
    }

    static void requireAudience(List<String> audiences) {
        if (audiences == null || audiences.stream().allMatch(a -> a == null || a.isBlank())) {
            throw new IllegalStateException("opencrm.security.jwt.audiences must name the API audience, "
                    + "for example api://opencrm-api (ADR-016 decision 1)");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
