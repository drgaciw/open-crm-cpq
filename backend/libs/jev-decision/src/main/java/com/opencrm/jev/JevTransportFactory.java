package com.opencrm.jev;

import com.opencrm.jev.transport.HttpJevTransport;
import com.opencrm.jev.transport.JevTransport;
import com.opencrm.jev.transport.JevWireFormat;
import com.opencrm.jev.transport.NoOpJevTransport;
import com.opencrm.jev.transport.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Chooses the transport from configuration and fails closed. Any configuration that is incomplete or
 * unsafe yields the no-op transport plus an error log, never an exception: a misconfigured advisory
 * feature must not stop its host service from starting.
 */
final class JevTransportFactory {

    private static final Logger log = LoggerFactory.getLogger(JevTransportFactory.class);
    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private JevTransportFactory() {
    }

    static JevTransport create(JevProperties properties, RestClient.Builder builder) {
        if (!properties.isEnabled() || !"http".equalsIgnoreCase(properties.getTransport())) {
            return new NoOpJevTransport();
        }
        String problem = problemWith(properties);
        if (problem != null) {
            log.error("Jev is enabled but misconfigured ({}); falling back to the no-op transport", problem);
            return new NoOpJevTransport();
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        RestClient restClient = builder.requestFactory(requestFactory).build();
        return new HttpJevTransport(restClient, URI.create(properties.getBaseUrl()), properties.getApiKey(),
                properties.getWireFormat(), new RetryPolicy(properties.getMaxRetries(), 500, 5_000),
                properties.getMaxBodyBytes());
    }

    /** @return a description of the first problem, or null when the HTTP transport may be built */
    static String problemWith(JevProperties properties) {
        if (isBlank(properties.getApiKey())) {
            return "opencrm.jev.api-key is blank";
        }
        if (isBlank(properties.getBaseUrl())) {
            return "opencrm.jev.base-url is blank";
        }
        URI uri;
        try {
            uri = URI.create(properties.getBaseUrl());
        } catch (IllegalArgumentException e) {
            return "opencrm.jev.base-url is not a valid URI";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !("http".equals(scheme) && LOOPBACK.contains(host))) {
            return "opencrm.jev.base-url must be https unless it targets localhost";
        }
        if (properties.getWireFormat() != JevWireFormat.FACADE) {
            String model = properties.getModel();
            if (isBlank(model)) {
                return "opencrm.jev.model is blank";
            }
            if (model.toLowerCase(Locale.ROOT).endsWith("latest")) {
                return "opencrm.jev.model must be pinned, not a *latest alias";
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
