package com.opencrm.gateway.routes;

import java.util.List;

/**
 * Published CPQ gateway route contract. Clients call {@value #CPQ_API_PREFIX} paths;
 * the gateway rewrites to aggregate-owning service routes under {@code /api/v1/...}.
 */
public final class CpqGatewayRoutes {

    public static final String CPQ_API_PREFIX = "/api/v1/cpq";

    public static final List<String> ROUTE_IDS = List.of(
            "cpq-pricing-service",
            "cpq-pricebook-service",
            "cpq-product-service",
            "cpq-quote-service",
            "cpq-configuration-engine",
            "cpq-approval-routing",
            "cpq-document-generation",
            "cpq-contract-asset-management"
    );

    private CpqGatewayRoutes() {
    }
}
