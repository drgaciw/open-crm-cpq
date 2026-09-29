package com.opencrm.security;

import com.opencrm.security.web.OpenCrmJwtDecoders;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import com.opencrm.security.web.OpenCrmSecurityProperties;
import com.opencrm.security.web.OpenCrmSecurityProperties.SecurityMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The ADR-016 go-live gate at startup (step 5). Every service, servlet or reactive, logs one line saying how it will
 * authenticate. With the production marker ({@code OPENCRM_ENV=prod}) it refuses to start unless:
 * <ul>
 *   <li>the mode is {@code jwt} (the mode check itself also refuses {@code dev});</li>
 *   <li>{@code opencrm.security.jwt.issuer-uri} is a tenant-specific issuer;</li>
 *   <li>{@code opencrm.security.jwt.audiences} contains the API's client id, a GUID. The issuer is the v2.0 endpoint,
 *       and v2.0 access tokens carry the client id as {@code aud}, never {@code api://opencrm-api}. Without it every
 *       token would be refused with 401: a whole outage, configured;</li>
 *   <li>no client secret is configured for the service identity;</li>
 *   <li>the actuator exposes nothing beyond {@code health} and {@code info} on the service port. Metrics leave a
 *       service only on a management port of its own ({@code management.server.port}, env
 *       {@code MANAGEMENT_SERVER_PORT}), which a network policy opens to the monitoring namespace alone (owner
 *       decision 4). Otherwise they would be readable with any user token.</li>
 * </ul>
 * All problems are reported together, so one failed rollout shows the whole list.
 */
@Slf4j
public final class OpenCrmIdentityPreflight {

    /** The actuator endpoints that may be served on the service port: neither leaks anything. */
    private static final Set<String> SERVICE_PORT_ENDPOINTS = Set.of("health", "info");

    private static final Pattern GUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private OpenCrmIdentityPreflight() {
    }

    /** The checked configuration's one-line summary, as a bean: creating it runs the gate. */
    public record Summary(String line) {
    }

    /**
     * Checks the configuration and logs the summary.
     *
     * @return the summary line
     * @throws IllegalStateException under the production marker, listing every problem
     */
    public static String check(OpenCrmSecurityProperties properties, Environment environment) {
        boolean production = OpenCrmSecurityAutoConfiguration.isProduction(environment);
        SecurityMode mode = OpenCrmSecurityAutoConfiguration.mode(properties, environment);
        OpenCrmSecurityProperties.Jwt jwt = properties.getJwt();
        String issuer = blankToNull(jwt.getIssuerUri());
        String clientId = firstNonBlank(properties.getClient().getClientId(), environment.getProperty("AZURE_CLIENT_ID"));
        String tokenFile = firstNonBlank(properties.getClient().getFederatedTokenFile(),
                environment.getProperty("AZURE_FEDERATED_TOKEN_FILE"));
        boolean secret = blankToNull(properties.getClient().getClientSecret()) != null;

        String summary = "ADR-016 identity: mode=" + mode.name().toLowerCase()
                + ", production=" + production
                + ", issuer=" + (issuer == null ? "none" : issuer)
                + ", audiences=" + jwt.getAudiences()
                + ", management port=" + managementPort(environment)
                + ", service identity=" + (clientId == null ? "none"
                        : clientId + (tokenFile != null ? " (workload identity)" : secret ? " (client secret)" : " (no credential)"));
        log.info(summary);
        if (!production) {
            return summary;
        }

        List<String> problems = new ArrayList<>();
        if (mode != SecurityMode.JWT) {
            problems.add("opencrm.security.mode must be jwt");
        }
        if (issuer == null) {
            problems.add("opencrm.security.jwt.issuer-uri must be the tenant issuer "
                    + "https://login.microsoftonline.com/<tenant-id>/v2.0");
        } else {
            try {
                OpenCrmJwtDecoders.requireSingleTenant(issuer);
            } catch (IllegalStateException multiTenant) {
                problems.add(multiTenant.getMessage());
            }
        }
        if (jwt.getAudiences() == null || jwt.getAudiences().stream().noneMatch(a -> a != null && GUID.matcher(a.trim()).matches())) {
            problems.add("opencrm.security.jwt.audiences must include the API's client id (a GUID): v2.0 access "
                    + "tokens carry it as aud, so " + jwt.getAudiences() + " would refuse every token");
        }
        if (secret) {
            problems.add("opencrm.security.client.client-secret must not be set; use workload identity");
        }
        Set<String> onServicePort = endpointsOnServicePort(environment);
        if (!onServicePort.isEmpty()) {
            problems.add("management.endpoints.web.exposure.include exposes " + onServicePort + " on the service port; "
                    + "set management.server.port (MANAGEMENT_SERVER_PORT) so the actuator has a port of its own that "
                    + "only the monitoring namespace can reach, or expose health and info only");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("ADR-016 go-live gate (opencrm.env=" + environment.getProperty("opencrm.env")
                    + "): " + String.join("; ", problems));
        }
        return summary;
    }

    /**
     * How the actuator is reached, by Spring Boot's own rule: {@code same} unless {@code management.server.port} is
     * set and differs from {@code server.port}; {@code disabled} for a negative port.
     */
    static String managementPort(Environment environment) {
        Integer management = port(environment, "management.server.port");
        if (management != null && management < 0) {
            return "disabled";
        }
        Integer server = port(environment, "server.port");
        boolean same = management == null
                || (server == null && management == 8080)
                || (management != 0 && management.equals(server));
        return same ? "same" : String.valueOf(management);
    }

    /**
     * The endpoints reachable on the service port beyond {@code health} and {@code info}. Empty when the actuator has
     * a port of its own or is off the network, or when nothing else is exposed. {@code *} counts as everything.
     */
    static Set<String> endpointsOnServicePort(Environment environment) {
        if (!"same".equals(managementPort(environment))) {
            return Set.of();
        }
        Binder binder = Binder.get(environment);
        List<String> include = binder.bind("management.endpoints.web.exposure.include", Bindable.listOf(String.class))
                .orElse(List.of());
        List<String> exclude = binder.bind("management.endpoints.web.exposure.exclude", Bindable.listOf(String.class))
                .orElse(List.of());
        Set<String> exposed = new LinkedHashSet<>();
        for (String id : include) {
            String endpoint = id.trim().toLowerCase(Locale.ROOT);
            boolean excluded = exclude.stream().anyMatch(e -> e.trim().equalsIgnoreCase(endpoint));
            if (!endpoint.isEmpty() && (endpoint.equals("*") || !excluded) && !SERVICE_PORT_ENDPOINTS.contains(endpoint)) {
                exposed.add(endpoint);
            }
        }
        return exposed;
    }

    private static Integer port(Environment environment, String property) {
        String value = blankToNull(environment.getProperty(property));
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (blankToNull(value) != null) {
                return value.trim();
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
