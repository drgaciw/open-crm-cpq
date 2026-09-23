package com.opencrm.security.web;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code opencrm.security.*}: the baseline security of every servlet service (ADR-016 decision 4).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "opencrm.security")
public class OpenCrmSecurityProperties {

    /**
     * {@code dev} permits everything (local development only); {@code jwt} makes the service an OAuth 2.0 resource
     * server. Unset means {@code dev} under the {@code dev} Spring profile and {@code jwt} everywhere else.
     */
    private SecurityMode mode;

    /** Token validation for {@code jwt} mode. */
    private final Jwt jwt = new Jwt();

    /** App role a service token needs for {@code /internal/**} (ADR-016 decision 4). */
    private String internalRole = "Internal.Invoke";

    /** Whether {@code /api-docs/**} and Swagger UI are public in {@code jwt} mode. Off unless a service opts in. */
    private boolean permitApiDocs = false;

    /**
     * Transitional, removed by ADR-016 rollout step 2: keeps {@code /internal/**} open to anonymous mesh callers, as
     * the pricebook and configuration-engine chains did before step 1, until those callers send a service token.
     * Logs a WARN at startup and is refused when the production marker is set. {@code SecurityBaselineFitnessTest}
     * pins which services may set it.
     */
    private boolean legacyOpenInternal = false;

    /** Local development versus a real identity provider. */
    public enum SecurityMode {
        /** Permit-all chain. Refused when the production marker ({@code opencrm.env=prod}) is set. */
        DEV,
        /** OAuth 2.0 resource server: bearer JWT from the configured tenant, for the configured audience. */
        JWT
    }

    @Getter
    @Setter
    public static class Jwt {
        /**
         * Tenant-specific issuer, for example {@code https://login.microsoftonline.com/<tenant-id>/v2.0}. Multi-tenant
         * issuers ({@code common}, {@code organizations}, {@code consumers}) are refused at startup.
         */
        private String issuerUri;

        /** JWKS endpoint; defaults to the issuer's discovery document when unset. */
        private String jwkSetUri;

        /** Accepted {@code aud} values; a token must carry at least one. */
        private List<String> audiences = new ArrayList<>(List.of("api://opencrm-api"));

        /** Claim holding app roles; Entra ID puts them in {@code roles}. */
        private String rolesClaim = "roles";

        /** Tolerated clock difference for {@code exp} and {@code nbf}. */
        private Duration clockSkew = Duration.ofSeconds(60);
    }
}
