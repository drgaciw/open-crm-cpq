package com.opencrm.gateway.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code opencrm.gateway.*}: the gateway as backend-for-frontend (ADR-016 decision 2). Token validation for the bearer
 * lane uses the shared {@code opencrm.security.jwt.*} settings, and the login uses the same tenant.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "opencrm.gateway")
public class GatewayBffProperties {

    private final Login login = new Login();
    private final Session session = new Session();

    /** OIDC login against the workforce Entra tenant (authorization code + PKCE, confidential client). */
    @Getter
    @Setter
    public static class Login {
        /**
         * Client id of the gateway's Entra app registration, a confidential web client. Unset means no browser login:
         * the gateway then accepts bearer tokens only, and refuses to start under the production marker.
         */
        private String clientId;

        /** Registration id, used in {@code /oauth2/authorization/{id}} and {@code /login/oauth2/code/{id}}. */
        private String registrationId = "entra";

        /**
         * Delegated scopes. {@code openid} and {@code profile} give the ID token, {@code offline_access} the refresh
         * token, and the API scope makes the access token one that every service accepts.
         */
        private List<String> scopes = new ArrayList<>(
                List.of("openid", "profile", "offline_access", "api://opencrm-api/.default"));

        /**
         * Projected service-account token sent as the client assertion (workload identity federation; the gateway's
         * app registration trusts the AKS service account). Defaults to {@code AZURE_FEDERATED_TOKEN_FILE}.
         */
        private String federatedTokenFile;

        /** Client secret, for local tests against a real tenant only. Refused when the production marker is set. */
        private String clientSecret;

        /** Where Entra sends the authorization code; a Spring template. */
        private String redirectUri = "{baseUrl}/login/oauth2/code/{registrationId}";

        /** Where the browser lands after login when no page was saved. */
        private String successUrl = "/";

        /** Where Entra sends the browser after RP-initiated logout; a Spring template. */
        private String postLogoutRedirectUri = "{baseUrl}/";
    }

    /** The browser's session cookie. The session itself, with the user's tokens, lives in Redis (Spring Session). */
    @Getter
    @Setter
    public static class Session {
        private String cookieName = "OPENCRM_SESSION";

        /** {@code Secure}; turn off only for plain-http local testing on a host other than localhost. */
        private boolean cookieSecure = true;
    }
}
