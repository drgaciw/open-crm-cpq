package com.opencrm.gateway.routes;

import java.util.List;

/**
 * Published CPQ gateway route contract. Clients call {@value #CPQ_API_PREFIX} paths;
 * the gateway rewrites to aggregate-owning service routes under {@code /api/v1/...}.
 */
public final class CpqGatewayRoutes {

    public static final String CPQ_API_PREFIX = "/api/v1/cpq";

    /**
     * Public {@code /api/v1/cpq/...} alias route ids (RewritePath → aggregate owners).
     */
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

    /**
     * Legacy {@code /api/v1/...} routes from CPQ_API_CONTRACT_MATRIX §9 / Appendix A
     * (agent-critical prefixes including price books).
     */
    public static final List<String> AGENT_CONTRACT_ROUTE_IDS = List.of(
            "crm-service",
            "pricing-service",
            "pricebook-service",
            "product-service",
            "quote-service",
            "configuration-engine",
            "approval-routing",
            "erp-stub"
    );

    /**
     * Aggregate owner route ids present in the default {@code application.yml} profile
     * beyond the CPQ public aliases ({@link #ROUTE_IDS}). Includes document/contract owners
     * and default-profile-only communication/ai routes (absent from the {@code dev} profile).
     */
    public static final List<String> DEFAULT_PROFILE_OWNER_ROUTE_IDS = List.of(
            "crm-service",
            "pricing-service",
            "pricebook-service",
            "product-service",
            "quote-service",
            "configuration-engine",
            "approval-routing",
            "document-generation",
            "contract-asset-management",
            "erp-stub",
            "communication-service",
            "ai-service"
    );

    /**
     * Every route id declared under the default spring.cloud.gateway.routes list
     * (owners + CPQ aliases). Removing any of these from yaml must fail unit tests.
     */
    public static final List<String> DEFAULT_PROFILE_ROUTE_IDS = List.of(
            "crm-service",
            "pricing-service",
            "cpq-pricing-service",
            "pricebook-service",
            "cpq-pricebook-service",
            "product-service",
            "cpq-product-service",
            "quote-service",
            "cpq-quote-service",
            "configuration-engine",
            "cpq-configuration-engine",
            "approval-routing",
            "cpq-approval-routing",
            "document-generation",
            "cpq-document-generation",
            "contract-asset-management",
            "cpq-contract-asset-management",
            "erp-stub",
            "communication-service",
            "ai-service"
    );

    private CpqGatewayRoutes() {
    }
}
