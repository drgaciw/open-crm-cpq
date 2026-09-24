package com.opencrm.gateway.security;

import com.opencrm.security.web.OpenCrmJwtDecoders;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import com.opencrm.security.web.OpenCrmSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeReactiveAuthenticationManager;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The BFF login (ADR-016 decision 2), active when {@code opencrm.gateway.login.client-id} is set.
 *
 * <ul>
 *   <li>One Entra registration, built from the tenant issuer without a discovery call at startup. No user-info endpoint:
 *       the user comes from the ID token, and the access token (audience: the OpenCRM API) is never sent to Graph.</li>
 *   <li>Authorization code with PKCE, as a confidential client. The client authenticates with its workload identity
 *       ({@link GatewayClientAuthentication}); a client secret is for local tests and refused under the production
 *       marker.</li>
 *   <li>The user's tokens are kept in the WebSession, which Spring Session stores in Redis, so they never reach the
 *       browser and every gateway replica sees them.</li>
 *   <li>Expired access tokens are refreshed with the refresh token, using the same client authentication.</li>
 * </ul>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@Conditional(GatewayLoginConfiguration.LoginConfigured.class)
public class GatewayLoginConfiguration {

    /** {@code https://<authority>/<tenant>/v2.0}: the v2.0 issuer of one workforce tenant. */
    private static final Pattern V2_TENANT_ISSUER = Pattern.compile("^(https://[^/]+)/([^/]+)/v2\\.0/?$");

    @Bean
    public GatewayLogin gatewayLogin(GatewayBffProperties bff, OpenCrmSecurityProperties security,
                                     Environment environment) {
        boolean production = OpenCrmSecurityAutoConfiguration.isProduction(environment);
        GatewayBffProperties.Login login = bff.getLogin();
        GatewayClientAuthentication clientAuthentication = clientAuthentication(login, environment, production);
        ClientRegistration registration = registration(login, security.getJwt().getIssuerUri(),
                clientAuthentication);
        ReactiveClientRegistrationRepository registrations = new InMemoryReactiveClientRegistrationRepository(registration);
        ServerOAuth2AuthorizedClientRepository authorizedClients = new WebSessionServerOAuth2AuthorizedClientRepository();

        WebClientReactiveAuthorizationCodeTokenResponseClient codeClient = codeClient(clientAuthentication);
        WebClientReactiveRefreshTokenTokenResponseClient refreshClient = refreshClient(clientAuthentication);

        ReactiveOAuth2AuthorizedClientProvider provider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken(refresh -> refresh.accessTokenResponseClient(refreshClient))
                .build();
        DefaultReactiveOAuth2AuthorizedClientManager clientManager =
                new DefaultReactiveOAuth2AuthorizedClientManager(registrations, authorizedClients);
        clientManager.setAuthorizedClientProvider(provider);

        OidcAuthorizationCodeReactiveAuthenticationManager authenticationManager =
                new OidcAuthorizationCodeReactiveAuthenticationManager(codeClient, new OidcReactiveOAuth2UserService());

        OidcClientInitiatedServerLogoutSuccessHandler logout =
                new OidcClientInitiatedServerLogoutSuccessHandler(registrations);
        logout.setPostLogoutRedirectUri(login.getPostLogoutRedirectUri());

        log.info("Browser login: Entra registration '{}' for client {} ({})", registration.getRegistrationId(),
                registration.getClientId(),
                registration.getClientAuthenticationMethod().equals(ClientAuthenticationMethod.NONE)
                        ? "workload identity" : "client secret");
        return new GatewayLogin(registration.getRegistrationId(), registrations, authorizedClients, clientManager,
                authenticationManager, logout, login.getSuccessUrl());
    }

    /** Redeems the authorization code, authenticating the gateway with {@code clientAuthentication}. */
    static WebClientReactiveAuthorizationCodeTokenResponseClient codeClient(
            GatewayClientAuthentication clientAuthentication) {
        WebClientReactiveAuthorizationCodeTokenResponseClient client =
                new WebClientReactiveAuthorizationCodeTokenResponseClient();
        client.addParametersConverter(clientAuthentication::parameters);
        return client;
    }

    /** Refreshes the user's access token, authenticating the gateway with {@code clientAuthentication}. */
    static WebClientReactiveRefreshTokenTokenResponseClient refreshClient(
            GatewayClientAuthentication clientAuthentication) {
        WebClientReactiveRefreshTokenTokenResponseClient client = new WebClientReactiveRefreshTokenTokenResponseClient();
        client.addParametersConverter(clientAuthentication::parameters);
        return client;
    }

    /** Workload identity when a federated token file is available, else a client secret outside production. */
    static GatewayClientAuthentication clientAuthentication(GatewayBffProperties.Login login, Environment environment,
                                                            boolean production) {
        String secret = blankToNull(login.getClientSecret());
        if (secret != null && production) {
            throw new IllegalStateException("opencrm.gateway.login.client-secret is not allowed when opencrm.env="
                    + environment.getProperty("opencrm.env") + ": use workload identity (ADR-016 decision 2)");
        }
        String tokenFile = blankToNull(login.getFederatedTokenFile());
        if (tokenFile == null) {
            tokenFile = blankToNull(environment.getProperty("AZURE_FEDERATED_TOKEN_FILE"));
        }
        if (tokenFile != null) {
            return GatewayClientAuthentication.federated(Path.of(tokenFile));
        }
        if (secret != null) {
            return GatewayClientAuthentication.secret();
        }
        throw new IllegalStateException("The gateway login needs a credential: AZURE_FEDERATED_TOKEN_FILE (workload "
                + "identity) or, for local tests, opencrm.gateway.login.client-secret (ADR-016 decision 2)");
    }

    /** The Entra registration, with endpoints derived from the tenant issuer. */
    static ClientRegistration registration(GatewayBffProperties.Login login, String issuerUri,
                                           GatewayClientAuthentication clientAuthentication) {
        String issuer = blankToNull(issuerUri);
        if (issuer == null) {
            throw new IllegalStateException("The gateway login needs opencrm.security.jwt.issuer-uri, the tenant "
                    + "issuer https://login.microsoftonline.com/<tenant-id>/v2.0 (ADR-016 decision 1)");
        }
        OpenCrmJwtDecoders.requireSingleTenant(issuer);
        Matcher tenant = V2_TENANT_ISSUER.matcher(issuer);
        if (!tenant.matches()) {
            throw new IllegalStateException("The gateway login needs a v2.0 tenant issuer "
                    + "(https://login.microsoftonline.com/<tenant-id>/v2.0), not " + issuer);
        }
        String base = tenant.group(1) + "/" + tenant.group(2);
        boolean secret = !clientAuthentication.usesFederatedAssertion();
        return ClientRegistration.withRegistrationId(login.getRegistrationId())
                .clientId(login.getClientId().trim())
                .clientSecret(secret ? login.getClientSecret().trim() : null)
                .clientAuthenticationMethod(secret
                        ? ClientAuthenticationMethod.CLIENT_SECRET_POST : ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(login.getRedirectUri())
                .scope(login.getScopes())
                .authorizationUri(base + "/oauth2/v2.0/authorize")
                .tokenUri(base + "/oauth2/v2.0/token")
                .jwkSetUri(base + "/discovery/v2.0/keys")
                .issuerUri(issuer)
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .providerConfigurationMetadata(Map.of("end_session_endpoint", base + "/oauth2/v2.0/logout"))
                .clientName("Microsoft Entra ID")
                .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build())
                .build();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** True when {@code opencrm.gateway.login.client-id} has a value. */
    static final class LoginConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("opencrm.gateway.login.client-id"));
        }
    }
}
