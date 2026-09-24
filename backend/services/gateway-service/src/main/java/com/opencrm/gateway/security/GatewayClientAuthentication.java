package com.opencrm.gateway.security;

import org.springframework.security.oauth2.client.endpoint.AbstractOAuth2AuthorizationGrantRequest;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * How the gateway authenticates itself at the Entra token endpoint when it redeems an authorization code or refreshes a
 * token. With workload identity federation the projected service-account token is the {@code jwt-bearer} client
 * assertion (RFC 7523), re-read on every request because kubelet rotates it; no secret exists anywhere. The registration
 * then uses {@code ClientAuthenticationMethod.NONE}, so Spring sends only {@code client_id} and this adds the assertion.
 * With a client secret (local tests only) Spring's {@code client_secret_post} does the work and this adds nothing.
 */
public final class GatewayClientAuthentication {

    static final String JWT_BEARER_ASSERTION = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    private final Path federatedTokenFile;

    private GatewayClientAuthentication(Path federatedTokenFile) {
        this.federatedTokenFile = federatedTokenFile;
    }

    /** Workload identity: the assertion is read from {@code federatedTokenFile} for every token request. */
    public static GatewayClientAuthentication federated(Path federatedTokenFile) {
        return new GatewayClientAuthentication(federatedTokenFile);
    }

    /** Client secret: Spring's own client authentication applies, nothing is added. */
    public static GatewayClientAuthentication secret() {
        return new GatewayClientAuthentication(null);
    }

    /** Whether the gateway authenticates with a federated assertion (true) or a client secret (false). */
    public boolean usesFederatedAssertion() {
        return federatedTokenFile != null;
    }

    /** Extra token-request parameters, for {@code addParametersConverter} of the reactive token response clients. */
    public MultiValueMap<String, String> parameters(AbstractOAuth2AuthorizationGrantRequest request) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        if (federatedTokenFile != null) {
            parameters.add("client_assertion_type", JWT_BEARER_ASSERTION);
            parameters.add("client_assertion", readAssertion());
        }
        return parameters;
    }

    private String readAssertion() {
        try {
            String assertion = Files.readString(federatedTokenFile, StandardCharsets.UTF_8).trim();
            if (!assertion.isEmpty()) {
                return assertion;
            }
        } catch (IOException e) {
            // reported below without the path's contents
        }
        throw new OAuth2AuthorizationException(new OAuth2Error("server_error",
                "Cannot read the gateway's federated token file (AZURE_FEDERATED_TOKEN_FILE)", null));
    }
}
