package com.opencrm.security.client;

import org.springframework.web.client.RestClientException;

/**
 * No service token could be obtained or attached. It is a {@link RestClientException}, so a {@code RestClient} caller
 * handles it like any other failed downstream call.
 */
public class ServiceTokenException extends RestClientException {

    public ServiceTokenException(String message) {
        super(message);
    }

    public ServiceTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
