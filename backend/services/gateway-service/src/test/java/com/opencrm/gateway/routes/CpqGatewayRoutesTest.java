package com.opencrm.gateway.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.gateway.controller.FallbackController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.params.provider.Arguments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Docker-free agent-contract smoke for CPQ_API_CONTRACT_MATRIX Appendix A.
 * <p>
 * Non-ERP rows: assert gateway {@code application.yml} Path predicates cover the route
 * (no full mesh). ERP rows: assert stub route config + {@link FallbackController} 501 body.
 * <p>
 * Run:
 * <pre>
 *   cd backend &amp;&amp; ./mvnw -pl services/gateway-service -am test -Punit-tests \
 *     -Dgroups=contract
 *   # or full fast CI path:
 *   bash scripts/ci/run-core.sh backend-unit
 * </pre>
 */
@Tag("unit")
@Tag("contract")
class CpqGatewayRoutesTest {

    private static final Path APPLICATION_YAML = Path.of("src/main/resources/application.yml");
    private static final Path APPENDIX_A =
            Path.of("src/test/resources/contracts/agent-contract-appendix-a.json");
    private static final Pattern PATH_PREDICATE = Pattern.compile("Path=([^\\n]+)");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static String yaml;
    private static JsonNode appendixRoutes;
    private static List<String> gatewayPathPatterns;

    @BeforeAll
    static void loadFixtures() throws Exception {
        yaml = Files.readString(APPLICATION_YAML);
        JsonNode root = MAPPER.readTree(APPENDIX_A.toFile());
        appendixRoutes = root.get("routes");
        assertNotNull(appendixRoutes, "Appendix A fixture must declare routes[]");
        assertTrue(appendixRoutes.isArray() && appendixRoutes.size() > 0,
                "Appendix A fixture must list at least one route");

        gatewayPathPatterns = new ArrayList<>();
        Matcher matcher = PATH_PREDICATE.matcher(yaml);
        while (matcher.find()) {
            String raw = matcher.group(1).trim();
            for (String part : raw.split(",")) {
                String pattern = part.trim();
                if (!pattern.isEmpty()) {
                    gatewayPathPatterns.add(pattern);
                }
            }
        }
        assertFalse(gatewayPathPatterns.isEmpty(), "application.yml must declare Path predicates");
    }

    static Stream<JsonNode> appendixARoutes() {
        return StreamSupport.stream(appendixRoutes.spliterator(), false);
    }

    static Stream<Arguments> appendixANonErpRoutes() {
        return appendixARoutes()
                .filter(r -> !r.path("erpStub").asBoolean(false))
                .map(r -> Arguments.of(r.get("matrixId").asText(), r));
    }

    static Stream<Arguments> appendixAErpRoutes() {
        return appendixARoutes()
                .filter(r -> r.path("erpStub").asBoolean(false))
                .map(r -> Arguments.of(r.get("matrixId").asText(), r));
    }

    @Test
    void applicationYamlDeclaresCpqRouteIds() {
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
    void applicationYamlRewritesCpqPathsToAggregateOwners() {
        assertTrue(yaml.contains("RewritePath=/api/v1/cpq/"),
                "CPQ routes must rewrite to owning service paths");
        assertTrue(yaml.contains("pricing|pricing-customers|rule-sets|rules"),
                "Pricing aggregate rewrite must be present");
        assertTrue(yaml.contains("configurations"),
                "Configuration engine rewrite must be present");
    }

    @Test
    void applicationYamlDeclaresErpStubRoute() {
        assertTrue(yaml.contains("id: erp-stub"),
                "ERP agent-contract paths must have a gateway stub route");
        assertTrue(yaml.contains("Path=/api/v1/erp/**"),
                "ERP stub must match /api/v1/erp/**");
        assertTrue(yaml.contains("forward:/fallback/erp"),
                "ERP stub must forward to local 501 handler");
    }

    @Test
    void applicationYamlRoutesAgentCriticalPrefixes() {
        assertTrue(yaml.contains("/api/v1/configuration/**"),
                "Configuration rules and validate must route to product-service");
        assertTrue(yaml.contains("Path=/api/v1/configurations/**"),
                "Configuration sessions must route to configuration-engine");
        assertTrue(yaml.contains("Path=/api/v1/pricing/**"),
                "Pricing execute/simulate must route to pricing-service");
        assertTrue(yaml.contains("/api/v1/pricing-customers/**"),
                "Pricing customers must route to pricing-service");
        assertTrue(yaml.contains("/api/v1/rules/**"),
                "DRL rules must route to pricing-service");
        assertTrue(yaml.contains("Path=/api/v1/price-books/**"),
                "Price books must route to pricebook-service");
        assertTrue(yaml.contains("Path=/api/v1/quotes/**"),
                "Quote and BFF approval paths must route to quote-service");
        assertTrue(yaml.contains("/api/v1/products/**"),
                "Product catalog must route to product-service");
        assertTrue(yaml.contains("/api/v1/product-bundles/**"),
                "Product bundles must route to product-service");
        assertTrue(yaml.contains("/api/v1/product-attributes/**"),
                "Product attributes must route to product-service");
        assertTrue(yaml.contains("/api/v1/contacts/**") || yaml.contains("/api/v1/contacts,"),
                "CRM contacts must route to crm-service");
        assertTrue(yaml.contains("/api/v1/deals/**"),
                "CRM deals must route to crm-service");
        assertTrue(yaml.contains("Path=/api/v1/erp/**"),
                "ERP agent-contract paths must hit erp-stub");
    }

    @Test
    void appendixAFixtureCoversEveryAgentContractRouteId() {
        List<String> fixtureRouteIds = new ArrayList<>();
        for (JsonNode route : appendixRoutes) {
            fixtureRouteIds.add(route.get("gatewayRouteId").asText());
        }
        for (String routeId : CpqGatewayRoutes.AGENT_CONTRACT_ROUTE_IDS) {
            // configuration-engine / approval-routing are agent-critical prefixes in §9
            // but not every id appears as an Appendix A agent-contract consumer path.
            if ("configuration-engine".equals(routeId) || "approval-routing".equals(routeId)) {
                continue;
            }
            assertTrue(
                    fixtureRouteIds.contains(routeId),
                    () -> "Appendix A fixture missing gateway route id: " + routeId);
        }
    }

    @ParameterizedTest(name = "matrix_{0}_nonErp_pathRouted")
    @MethodSource("appendixANonErpRoutes")
    @DisplayName("Appendix A non-ERP paths are covered by gateway Path predicates")
    void appendixA_nonErp_pathRoutedNot404(String matrixId, JsonNode route) {
        String examplePath = route.get("examplePath").asText();
        String gatewayRouteId = route.get("gatewayRouteId").asText();

        assertTrue(
                yaml.contains("id: " + gatewayRouteId),
                () -> matrixId + ": missing route id " + gatewayRouteId);
        assertTrue(
                CpqGatewayRoutes.AGENT_CONTRACT_ROUTE_IDS.contains(gatewayRouteId),
                () -> matrixId + ": " + gatewayRouteId + " not in AGENT_CONTRACT_ROUTE_IDS");
        assertTrue(
                matchesAnyGatewayPattern(examplePath),
                () -> matrixId + ": example path " + examplePath
                        + " is not covered by any Path predicate (would 404 at gateway)");
    }

    @ParameterizedTest(name = "matrix_{0}_erpStub_routedToFallback")
    @MethodSource("appendixAErpRoutes")
    @DisplayName("Appendix A ERP paths are reserved on erp-stub")
    void appendixA_erp_pathRoutedToStub(String matrixId, JsonNode route) {
        String examplePath = route.get("examplePath").asText();

        assertEquals("erp-stub", route.get("gatewayRouteId").asText(), matrixId);
        assertTrue(
                matchesAnyGatewayPattern(examplePath),
                () -> matrixId + ": " + examplePath + " must match Path=/api/v1/erp/**");
        assertTrue(yaml.contains("forward:/fallback/erp"),
                () -> matrixId + ": erp-stub must forward to FallbackController");
    }

    @Test
    @DisplayName("matrix_A-ERP-COSTS_returns501ReservedMessage")
    void matrix_A_ERP_COSTS_returns501ReservedMessage() {
        assertErpStub501();
    }

    @Test
    @DisplayName("matrix_A-ERP-COSTS-BULK_returns501ReservedMessage")
    void matrix_A_ERP_COSTS_BULK_returns501ReservedMessage() {
        assertErpStub501();
    }

    @Test
    @DisplayName("matrix_A-ERP-INVENTORY_returns501ReservedMessage")
    void matrix_A_ERP_INVENTORY_returns501ReservedMessage() {
        assertErpStub501();
    }

    private static void assertErpStub501() {
        FallbackController controller = new FallbackController();
        ResponseEntity<Map<String, Object>> response = controller.erpNotImplemented();

        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(Boolean.FALSE, response.getBody().get("success"));
        assertEquals("erp-adapter-service", response.getBody().get("service"));
        Object message = response.getBody().get("message");
        assertNotNull(message);
        assertTrue(
                message.toString().contains("Agent-contract ERP paths are reserved"),
                "ERP stub must advertise reserved agent-contract message");
    }

    private static boolean matchesAnyGatewayPattern(String examplePath) {
        for (String pattern : gatewayPathPatterns) {
            if (PATH_MATCHER.match(pattern, examplePath)) {
                return true;
            }
        }
        return false;
    }
}
