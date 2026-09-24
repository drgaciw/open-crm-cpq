package com.opencrm.security;

import com.opencrm.security.web.OpenCrmJwtDecoders;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import com.opencrm.security.web.OpenCrmSecurityProperties;
import com.opencrm.security.web.OpenCrmSecurityProperties.SecurityMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
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
 *   <li>no client secret is configured for the service identity.</li>
 * </ul>
 * All problems are reported together, so one failed rollout shows the whole list.
 */
@Slf4j
public final class OpenCrmIdentityPreflight {

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
        if (!problems.isEmpty()) {
            throw new IllegalStateException("ADR-016 go-live gate (opencrm.env=" + environment.getProperty("opencrm.env")
                    + "): " + String.join("; ", problems));
        }
        return summary;
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
