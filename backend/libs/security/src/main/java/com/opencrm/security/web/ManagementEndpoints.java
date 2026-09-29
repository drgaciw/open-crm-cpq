package com.opencrm.security.web;

import java.util.List;

/**
 * The actuator endpoints served on a management port without a token (owner decision 4): the ones a platform reads
 * without an identity. Nothing else is served there. Both edges, the servlet baseline and the reactive gateway, take
 * the list from here so that they cannot drift apart.
 */
public final class ManagementEndpoints {

    /** Liveness and readiness probes, build information, and the Prometheus scrape. */
    private static final List<String> PUBLIC = List.of("health", "info", "prometheus");

    private ManagementEndpoints() {
    }

    /** The endpoint ids, as {@code EndpointRequest.to(String...)} takes them. */
    public static String[] publicIds() {
        return PUBLIC.toArray(String[]::new);
    }
}