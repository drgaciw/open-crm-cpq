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
}
