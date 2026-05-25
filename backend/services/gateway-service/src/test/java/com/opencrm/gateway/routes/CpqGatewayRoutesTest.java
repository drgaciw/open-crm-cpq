package com.opencrm.gateway.routes;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class CpqGatewayRoutesTest {

    @Test
    void applicationYamlDeclaresCpqRouteIds() throws Exception {
        Path config = Path.of("src/main/resources/application.yml");
        String yaml = Files.readString(config);

        assertTrue(yaml.contains(CpqGatewayRoutes.CPQ_API_PREFIX + "/"),
                "CPQ public prefix must be configured");

        for (String routeId : CpqGatewayRoutes.ROUTE_IDS) {
            assertTrue(
                    yaml.contains("id: " + routeId),
                    () -> "Missing gateway route id: " + routeId);
        }

        for (String routeId : CpqGatewayRoutes.AGENT_CONTRACT_ROUTE_IDS) {
            assertTrue(
                    yaml.contains("id: " + routeId),
                    () -> "Missing agent-contract gateway route id: " + routeId);
        }
    }

    @Test
    void applicationYamlRewritesCpqPathsToAggregateOwners() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(yaml.contains("RewritePath=/api/v1/cpq/"),
                "CPQ routes must rewrite to owning service paths");
        assertTrue(yaml.contains("pricing|pricing-customers|rule-sets|rules"),
                "Pricing aggregate rewrite must be present");
        assertTrue(yaml.contains("configurations"),
                "Configuration engine rewrite must be present");
    }

    @Test
    void applicationYamlDeclaresErpStubRoute() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(yaml.contains("id: erp-stub"),
                "ERP agent-contract paths must have a gateway stub route");
        assertTrue(yaml.contains("Path=/api/v1/erp/**"),
                "ERP stub must match /api/v1/erp/**");
        assertTrue(yaml.contains("forward:/fallback/erp"),
                "ERP stub must forward to local 501 handler");
    }

    @Test
    void applicationYamlRoutesAgentCriticalPrefixes() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(yaml.contains("/api/v1/configuration/**"),
                "Configuration rules and validate must route to product-service");
        assertTrue(yaml.contains("Path=/api/v1/configurations/**"),
                "Configuration sessions must route to configuration-engine");
        assertTrue(yaml.contains("Path=/api/v1/pricing/**"),
                "Pricing execute/simulate must route to pricing-service");
        assertTrue(yaml.contains("/api/v1/rules/**"),
                "DRL rules must route to pricing-service");
        assertTrue(yaml.contains("Path=/api/v1/quotes/**"),
                "Quote and BFF approval paths must route to quote-service");
    }
}
