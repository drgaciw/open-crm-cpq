package com.opencrm.security.client;

import com.opencrm.security.web.OpenCrmSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the {@link ServiceTokenProvider} from {@code opencrm.security.client.*}, falling back to the environment that
 * the AKS workload identity webhook injects ({@code AZURE_CLIENT_ID}, {@code AZURE_TENANT_ID},
 * {@code AZURE_AUTHORITY_HOST}, {@code AZURE_FEDERATED_TOKEN_FILE}).
 *
 * <ul>
 *   <li>No client id: no service identity ({@link ServiceTokenProvider#none()}), the normal local-development case.</li>
 *   <li>A client id without a credential: no service identity, with a WARN.</li>
 *   <li>A client secret with the production marker set: refused at startup. Production uses workload identity, and no
 *       secret lives in configuration (ADR-016 decision 5 and the go-live gate).</li>
 * </ul>
 */
@Slf4j
public final class ServiceTokenProviders {

    static final String DEFAULT_AUTHORITY_HOST = "https://login.microsoftonline.com/";

    /** {@code https://<host>/<tenant>/v2.0} (v2.0) or {@code https://sts.windows.net/<tenant>/} (v1.0). */
    private static final Pattern V2_ISSUER = Pattern.compile("^(https://[^/]+)/([^/]+)/v2\\.0/?$");
    private static final Pattern V1_ISSUER = Pattern.compile("^https://sts\\.windows\\.net/([^/]+)/?$");

    private ServiceTokenProviders() {
    }

    public static ServiceTokenProvider create(OpenCrmSecurityProperties properties, Environment environment,
                                              boolean production) {
        OpenCrmSecurityProperties.Client client = properties.getClient();
        String clientId = firstNonBlank(client.getClientId(), environment.getProperty("AZURE_CLIENT_ID"));
        String tokenFile = firstNonBlank(client.getFederatedTokenFile(),
                environment.getProperty("AZURE_FEDERATED_TOKEN_FILE"));
        String secret = blankToNull(client.getClientSecret());
        if (secret != null && production) {
            throw new IllegalStateException("opencrm.security.client.client-secret is not allowed when opencrm.env="
                    + environment.getProperty("opencrm.env") + ": use workload identity (ADR-016 decision 5)");
        }
        if (clientId == null) {
            log.info("No service identity (opencrm.security.client.client-id or AZURE_CLIENT_ID): calls that need a "
                    + "service token are sent without one.");
            return ServiceTokenProvider.none();
        }
        if (tokenFile == null && secret == null) {
            log.warn("Service identity {} has no credential (AZURE_FEDERATED_TOKEN_FILE or "
                    + "opencrm.security.client.client-secret): calls that need a service token are sent without one.",
                    clientId);
            return ServiceTokenProvider.none();
        }
        URI tokenUri = tokenUri(client, properties.getJwt(), environment);
        String scope = firstNonBlank(client.getScope(), defaultScope(properties.getJwt().getAudiences()));
        Clock clock = Clock.systemUTC();
        ClientCredentialsServiceTokenProvider provider = tokenFile != null
                ? ClientCredentialsServiceTokenProvider.federated(tokenUri, clientId, scope, Path.of(tokenFile),
                        client.getRefreshBeforeExpiry(), client.getTimeout(), clock)
                : ClientCredentialsServiceTokenProvider.secret(tokenUri, clientId, scope, secret,
                        client.getRefreshBeforeExpiry(), client.getTimeout(), clock);
        log.info("Service identity {} ({}) gets tokens for scope {} from {}", clientId,
                tokenFile != null ? "workload identity" : "client secret", scope, tokenUri);
        return provider;
    }

    /** Explicit, else the workload identity authority and tenant, else the tenant of the configured issuer. */
    static URI tokenUri(OpenCrmSecurityProperties.Client client, OpenCrmSecurityProperties.Jwt jwt,
                        Environment environment) {
        String explicit = blankToNull(client.getTokenUri());
        if (explicit != null) {
            return URI.create(explicit);
        }
        String tenant = blankToNull(environment.getProperty("AZURE_TENANT_ID"));
        if (tenant != null) {
            String authority = firstNonBlank(environment.getProperty("AZURE_AUTHORITY_HOST"), DEFAULT_AUTHORITY_HOST);
            return URI.create((authority.endsWith("/") ? authority : authority + "/") + tenant + "/oauth2/v2.0/token");
        }
        String issuer = blankToNull(jwt.getIssuerUri());
        if (issuer != null) {
            Matcher v2 = V2_ISSUER.matcher(issuer);
            if (v2.matches()) {
                return URI.create(v2.group(1) + "/" + v2.group(2) + "/oauth2/v2.0/token");
            }
            Matcher v1 = V1_ISSUER.matcher(issuer);
            if (v1.matches()) {
                return URI.create(DEFAULT_AUTHORITY_HOST + v1.group(1) + "/oauth2/v2.0/token");
            }
        }
        throw new IllegalStateException("Cannot derive the token endpoint of the service identity: set "
                + "opencrm.security.client.token-uri, AZURE_TENANT_ID, or a tenant opencrm.security.jwt.issuer-uri");
    }

    /** {@code <first accepted audience>/.default}, for example {@code api://opencrm-api/.default}. */
    static String defaultScope(List<String> audiences) {
        String audience = audiences == null ? null
                : audiences.stream().map(ServiceTokenProviders::blankToNull).filter(a -> a != null).findFirst()
                        .orElse(null);
        if (audience == null) {
            throw new IllegalStateException("Set opencrm.security.client.scope or opencrm.security.jwt.audiences");
        }
        return audience.endsWith("/.default") ? audience : audience + "/.default";
    }

    private static String firstNonBlank(String first, String second) {
        String value = blankToNull(first);
        return value != null ? value : blankToNull(second);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
