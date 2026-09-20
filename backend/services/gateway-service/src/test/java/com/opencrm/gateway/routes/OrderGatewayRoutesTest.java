package com.opencrm.gateway.routes;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class OrderGatewayRoutesTest {
    @Test void bothProfilesRouteOrdersToTheirOwnerWithoutRewritingCommercialData() throws Exception {
        for (String yaml : Files.readString(Path.of("src/main/resources/application.yml")).split("(?m)^---")) {
            var profile = yaml.contains("on-profile: dev") ? "dev" : "default";
            assertTrue(yaml.contains("id: order-management"), profile);
            assertTrue(yaml.contains("${ORDER_MANAGEMENT_URI:http://localhost:8090}"), profile);
            assertTrue(yaml.contains("Path=/api/v1/orders/**,/api/v1/order-exceptions/**"), profile);
        }
    }
}
