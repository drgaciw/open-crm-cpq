package com.opencrm.gateway.security;

import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;

/**
 * Everything the security chain and the relay filter need from the configured browser login. The bean exists only when
 * {@code opencrm.gateway.login.client-id} is set ({@link GatewayLoginConfiguration}).
 *
 * @param registrationId      the Entra registration id ({@code entra})
 * @param registrations       the one client registration
 * @param authorizedClients   where the user's tokens live: the WebSession, stored in Redis by Spring Session
 * @param authorizedClientManager hands out a valid access token, refreshing it when it has expired
 * @param authenticationManager redeems the authorization code (with this gateway's client assertion) and validates the ID token
 * @param logoutSuccessHandler RP-initiated logout at Entra
 * @param successUrl          where the browser lands after login when no page was saved
 */
public record GatewayLogin(String registrationId,
                           ReactiveClientRegistrationRepository registrations,
                           ServerOAuth2AuthorizedClientRepository authorizedClients,
                           ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
                           ReactiveAuthenticationManager authenticationManager,
                           ServerLogoutSuccessHandler logoutSuccessHandler,
                           String successUrl) {

    /** Where a browser starts the login: {@code /oauth2/authorization/entra}. */
    public String loginPath() {
        return "/oauth2/authorization/" + registrationId;
    }
}
