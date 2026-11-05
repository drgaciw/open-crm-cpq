package com.opencrm.gateway.routes;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class OrderGatewayRoutesTest {
    @Test void bothProfilesRouteOrdersToTheirOwnerWithoutRewritingCommercialData() throws Exception {
        for (String yaml : Files.readString(Path.of("src/main/resources/application.yml")).split("(?m)^---")) {
            var profile = yaml.contains("on-profile: dev") ? "dev" : "default";
            assertTrue(yaml.contains("id: order-management"), profile);
            assertTrue(yaml.contains("${ORDER_MANAGEMENT_URI:http://localhost:8091}"), profile);
            assertTrue(yaml.contains("Path=/api/v1/orders/**,/api/v1/order-exceptions/**"), profile);
            var block=Pattern.compile("(?ms)^\\s*- id: order-management\\s*$(.*?)(?=^\\s*- id:|\\z)").matcher(yaml);
            assertTrue(block.find(),profile);
            var path=Pattern.compile("Path=([^\\r\\n]+)").matcher(block.group(1));
            assertTrue(path.find(),profile);
            var config=new PathRoutePredicateFactory.Config().setPatterns(List.of(path.group(1).trim().split(",")));
            var predicate=new PathRoutePredicateFactory(new org.springframework.boot.webflux.autoconfigure.WebFluxProperties()).apply(config);
            for(String uri:List.of("/api/v1/orders","/api/v1/orders/123","/api/v1/order-exceptions","/api/v1/order-outbox","/api/v1/order-outbox/123/recover"))
                assertTrue(predicate.test(MockServerWebExchange.from(MockServerHttpRequest.get(uri))),profile+": "+uri);
            for(String uri:List.of("/api/v1/order-outbox-evil","/api/v1/order-outboxes/123","/api/v1/accounts"))
                assertFalse(predicate.test(MockServerWebExchange.from(MockServerHttpRequest.get(uri))),profile+": "+uri);
        }
    }
}
