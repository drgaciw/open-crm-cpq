package com.opencrm.security.client;

import java.util.Optional;

/**
 * Access tokens for this service's own identity, obtained with the OAuth 2.0 client-credentials grant
 * (ADR-016 decision 5). Machine-initiated calls and calls to another service's {@code /internal/**} use them.
 */
@FunctionalInterface
public interface ServiceTokenProvider {

    /**
     * A bearer access token for this service, or empty when no service identity is configured (local development).
     *
     * @throws ServiceTokenException when an identity is configured but no token can be obtained
     */
    Optional<String> token();

    /** No service identity: every call to {@link #token()} is empty. */
    static ServiceTokenProvider none() {
        return Optional::empty;
    }
}
