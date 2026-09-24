package com.opencrm.security.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * How a service authenticates its calls to other services (ADR-016 decision 5). Every {@code RestClient} that calls
 * another OpenCRM service takes exactly one of these interceptors; {@code SecurityBaselineFitnessTest} checks that.
 *
 * <ul>
 *   <li>{@link #relayUserToken()} for user-initiated calls to another service's {@code /api/v1/**}: the caller's
 *       bearer token is forwarded, so the downstream service sees the real actor. Where there is no user token (a Kafka
 *       consumer, a scheduler, a thread without the security context), this service's own token is sent instead.</li>
 *   <li>{@link #serviceToken()} for calls to another service's {@code /internal/**}, which needs the
 *       {@code Internal.Invoke} app role that only service identities hold: always this service's own token.</li>
 * </ul>
 *
 * <p>An {@code Authorization} header the caller set explicitly is left alone. When no token is available the request
 * goes out without one and a WARN is logged once; that is the local {@code dev} case, where every service permits all.
 * With the production marker set in {@code jwt} mode, a missing token is a {@link ServiceTokenException} instead of a
 * request that the other service would reject.
 */
@Slf4j
public final class OutboundAuth {

    private final ServiceTokenProvider serviceTokens;
    private final boolean requireToken;
    private final AtomicBoolean warnedAboutMissingToken = new AtomicBoolean();

    /**
     * @param serviceTokens this service's own tokens
     * @param requireToken  whether a request without any token is an error (production marker in {@code jwt} mode)
     */
    public OutboundAuth(ServiceTokenProvider serviceTokens, boolean requireToken) {
        this.serviceTokens = serviceTokens;
        this.requireToken = requireToken;
    }

    /** No service identity and no token required: local development and tests. */
    public static OutboundAuth withoutServiceIdentity() {
        return new OutboundAuth(ServiceTokenProvider.none(), false);
    }

    /** For user-initiated calls to {@code /api/v1/**}: the caller's token, else this service's token. */
    public ClientHttpRequestInterceptor relayUserToken() {
        return this::relay;
    }

    /** For calls to {@code /internal/**}: always this service's own token. */
    public ClientHttpRequestInterceptor serviceToken() {
        return this::service;
    }

    private ClientHttpResponse relay(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!hasAuthorization(request)) {
            Optional<String> token = currentUserToken();
            attach(request, token.isPresent() ? token : serviceTokens.token());
        }
        return execution.execute(request, body);
    }

    private ClientHttpResponse service(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!hasAuthorization(request)) {
            attach(request, serviceTokens.token());
        }
        return execution.execute(request, body);
    }

    private void attach(HttpRequest request, Optional<String> token) {
        if (token.isPresent()) {
            request.getHeaders().setBearerAuth(token.get());
            return;
        }
        if (requireToken) {
            throw new ServiceTokenException("No bearer token for " + request.getMethod() + " "
                    + request.getURI().getPath() + ": configure this service's identity (AZURE_CLIENT_ID and "
                    + "AZURE_FEDERATED_TOKEN_FILE) (ADR-016 decision 5)");
        }
        if (warnedAboutMissingToken.compareAndSet(false, true)) {
            log.warn("Calling {} {} without a bearer token: there is no user token and no service identity. Expected "
                    + "only with opencrm.security.mode=dev; logged once.", request.getMethod(),
                    request.getURI().getPath());
        }
    }

    /** The bearer token of the current request's authenticated caller, if there is one. */
    static Optional<String> currentUserToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> bearer && bearer.isAuthenticated()) {
            return Optional.of(bearer.getToken().getTokenValue());
        }
        return Optional.empty();
    }

    private static boolean hasAuthorization(HttpRequest request) {
        return request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) != null;
    }
}
