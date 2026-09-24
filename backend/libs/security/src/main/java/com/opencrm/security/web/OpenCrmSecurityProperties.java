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

    /** This service's own identity for machine calls and {@code /internal/**} calls (ADR-016 decision 5). */
    private final Client client = new Client();

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

    /**
     * {@code opencrm.security.client.*}: the OAuth 2.0 client-credentials identity of this service. In AKS every value
     * defaults from the workload identity webhook's environment ({@code AZURE_CLIENT_ID}, {@code AZURE_TENANT_ID},
     * {@code AZURE_AUTHORITY_HOST}, {@code AZURE_FEDERATED_TOKEN_FILE}), so no configuration and no secret is needed.
     */
    @Getter
    @Setter
    public static class Client {
        /** Application (client) id. Defaults to {@code AZURE_CLIENT_ID}. Unset means no service identity. */
        private String clientId;

        /**
         * Token endpoint. Defaults to {@code <AZURE_AUTHORITY_HOST><AZURE_TENANT_ID>/oauth2/v2.0/token}, else to the
         * endpoint of the tenant in {@code opencrm.security.jwt.issuer-uri}.
         */
        private String tokenUri;

        /** Requested scope. Defaults to the first accepted audience plus {@code /.default}. */
        private String scope;

        /**
         * Projected Kubernetes service-account token sent as the client assertion (workload identity federation).
         * Defaults to {@code AZURE_FEDERATED_TOKEN_FILE}. Re-read on every token request, because kubelet rotates it.
         */
        private String federatedTokenFile;

        /** Client secret, for local tests against a real tenant only. Refused when the production marker is set. */
        private String clientSecret;

        /** How long before expiry a cached token is replaced. */
        private Duration refreshBeforeExpiry = Duration.ofMinutes(5);

        /** Connect and read timeout of token requests. */
        private Duration timeout = Duration.ofSeconds(10);
    }
}
