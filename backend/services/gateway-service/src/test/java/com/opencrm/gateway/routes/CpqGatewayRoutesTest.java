package com.opencrm.gateway.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.gateway.controller.FallbackController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.AntPathMatcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Docker-free agent-contract smoke for CPQ_API_CONTRACT_MATRIX Appendix A.
 * <p>
 * Non-ERP rows: assert gateway {@code application.yml} Path predicates cover the route
 * (no full mesh). ERP rows: assert stub route config + HTTP 501 JSON from
 * {@link FallbackController} via {@link WebTestClient} (no Redis / Spring context).
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
@Execution(ExecutionMode.SAME_THREAD)
class CpqGatewayRoutesTest {

    private static final Path APPLICATION_YAML = Path.of("src/main/resources/application.yml");
    private static final Path APPENDIX_A =
            Path.of("src/test/resources/contracts/agent-contract-appendix-a.json");
    private static final Pattern PATH_PREDICATE = Pattern.compile("Path=([^\\n]+)");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** Documented ERP 501 body fields (CPQ_API_CONTRACT_MATRIX §7c / S2A-7). */
    private static final Set<String> ERP_501_JSON_FIELDS =
            Set.of("success", "message", "service", "timestamp");
    private static final String ERP_501_MESSAGE_FRAGMENT =
            "Agent-contract ERP paths are reserved";
    private static final String ERP_FORWARD_FALLBACK_PATH = "/fallback/erp/unavailable";

    private static String yaml;
    private static JsonNode appendixRoutes;
    private static List<String> gatewayPathPatterns;
    private static WebTestClient erpFallbackClient;

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

        // Docker-free HTTP: bind FallbackController only (no Redis / Security / Gateway context).
        erpFallbackClient = WebTestClient
                .bindToController(new FallbackController())
                .configureClient()
                .responseTimeout(Duration.ofSeconds(30))
                .build();
        // Warm the client so first assertion is not racing validator init under parallel suites.
        assertErpHttp501(ERP_FORWARD_FALLBACK_PATH);
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
    @DisplayName("Boot 4 / SCG server-webflux: routes live under spring.cloud.gateway.server.webflux")
    void applicationYamlUsesServerWebfluxGatewayPropertyPrefix() {
        assertTrue(
                yaml.contains("server:\n        webflux:")
                        || yaml.contains("server:\r\n        webflux:"),
                "Gateway routes must use spring.cloud.gateway.server.webflux (SCG 5 / Boot 4); "
                        + "legacy spring.cloud.gateway.routes is ignored and yields empty routing");
        assertTrue(yaml.contains("webflux:\n          routes:")
                        || yaml.contains("webflux:\r\n          routes:"),
                "webflux.routes must be nested under gateway.server.webflux");
    }

    @Test
    @DisplayName("management.endpoint.gateway.access must not nest under server.webflux")
    void applicationYamlEnablesGatewayActuatorEndpoint() {
        assertTrue(
                yaml.contains("include: health,info,metrics,gateway,prometheus"),
                "gateway must be in management.endpoints.web.exposure.include");
        assertTrue(
                yaml.contains("gateway:\n      access: read-only")
                        || yaml.contains("gateway:\r\n      access: read-only")
                        || yaml.contains("gateway:\n      enabled: true")
                        || yaml.contains("gateway:\r\n      enabled: true"),
                "management.endpoint.gateway must set access (Boot 4) or enabled");
        assertFalse(
                yaml.contains("gateway:\n      server:\n        webflux:\n          enabled:")
                        || yaml.contains("gateway:\r\n      server:\r\n        webflux:\r\n          enabled:"),
                "management.endpoint.gateway must not nest under server.webflux "
                        + "(that namespace is for routes/filters only)");
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
    @DisplayName("default profile declares every published route id (owners + CPQ aliases)")
    void applicationYamlDeclaresDefaultProfileRouteIds() {
        for (String routeId : CpqGatewayRoutes.DEFAULT_PROFILE_ROUTE_IDS) {
            assertTrue(
                    yaml.contains("id: " + routeId),
                    () -> "Missing default-profile gateway route id: " + routeId);
        }
        for (String routeId : CpqGatewayRoutes.DEFAULT_PROFILE_OWNER_ROUTE_IDS) {
            assertTrue(
                    yaml.contains("id: " + routeId),
                    () -> "Missing default-profile owner route id: " + routeId);
        }
        assertTrue(
                CpqGatewayRoutes.DEFAULT_PROFILE_ROUTE_IDS.containsAll(CpqGatewayRoutes.ROUTE_IDS),
                "DEFAULT_PROFILE_ROUTE_IDS must include all CPQ alias ROUTE_IDS");
        assertTrue(
                CpqGatewayRoutes.DEFAULT_PROFILE_ROUTE_IDS.containsAll(
                        CpqGatewayRoutes.AGENT_CONTRACT_ROUTE_IDS),
                "DEFAULT_PROFILE_ROUTE_IDS must include all AGENT_CONTRACT_ROUTE_IDS");
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
        assertTrue(yaml.contains("uri: forward:/fallback/erp/unavailable"),
                "ERP stub must forward to " + ERP_FORWARD_FALLBACK_PATH);
    }

    @Test
    void applicationYamlRoutesAgentCriticalPrefixes() {
        assertTrue(yaml.contains("/api/v1/configuration/**"),
                "Configuration rules CRUD must remain on product-service via configuration/**");
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
    @DisplayName("F3 S2B-11: POST configuration/validate rewrites to CE evaluate before product wildcard")
    void applicationYamlDeclaresConfigurationValidateShim() {
        assertTrue(yaml.contains("id: configuration-validate"),
                "Missing configuration-validate route (agent POST validate → CE)");
        assertTrue(yaml.contains("id: cpq-configuration-validate"),
                "Missing cpq-configuration-validate alias route");
        assertTrue(yaml.contains("Path=/api/v1/configuration/validate"),
                "Validate shim must match POST /api/v1/configuration/validate");
        assertTrue(yaml.contains("Path=/api/v1/cpq/configuration/validate"),
                "CPQ validate alias must match POST /api/v1/cpq/configuration/validate");
        // ADR-016 step 2: CE's public evaluate, never /internal/** (which refuses user tokens).
        assertTrue(yaml.contains("RewritePath=/api/v1/configuration/validate, /api/v1/configurations/evaluate"),
                "Validate must rewrite to CE POST /api/v1/configurations/evaluate");
        assertTrue(yaml.contains(
                        "RewritePath=/api/v1/cpq/configuration/validate, /api/v1/configurations/evaluate"),
                "CPQ validate alias must rewrite to CE evaluate");
        assertFalse(yaml.contains("/internal/"), "The gateway must not route onto /internal/** (ADR-016)");
        assertTrue(yaml.contains("CONFIGURATION_ENGINE_URI") || yaml.contains("http://localhost:8088"),
                "Validate shim must target configuration-engine URI");

        // Predicate order: first occurrence of validate Path must precede configuration/**
        int validateIdx = yaml.indexOf("Path=/api/v1/configuration/validate");
        int wildcardIdx = yaml.indexOf("/api/v1/configuration/**");
        assertTrue(validateIdx >= 0 && wildcardIdx >= 0,
                "Both validate Path and configuration/** must be present");
        assertTrue(validateIdx < wildcardIdx,
                "configuration-validate Path must be declared before product configuration/**");

        int cpqValidateIdx = yaml.indexOf("Path=/api/v1/cpq/configuration/validate");
        int cpqWildcardIdx = yaml.indexOf("/api/v1/cpq/configuration/**");
        assertTrue(cpqValidateIdx >= 0 && cpqWildcardIdx >= 0,
                "Both CPQ validate Path and cpq/configuration/** must be present");
        assertTrue(cpqValidateIdx < cpqWildcardIdx,
                "cpq-configuration-validate Path must precede cpq/configuration/**");
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
    @DisplayName("matrix_A-ERP-forwardTarget_returns501DocumentedJson")
    void matrix_A_ERP_configuredForwardTarget_returns501DocumentedJson() {
        assertErpHttp501(ERP_FORWARD_FALLBACK_PATH);
    }

    @ParameterizedTest(name = "matrix_{0}_returns501DocumentedJson")
    @MethodSource("appendixAErpRoutes")
    @DisplayName("Appendix A ERP reserved paths return HTTP 501 with documented JSON")
    void appendixA_erp_http501DocumentedJson(String matrixId, JsonNode route) {
        String examplePath = route.get("examplePath").asText();
        assertTrue(examplePath.startsWith("/api/v1/erp/"),
                () -> matrixId + ": ERP examplePath must be under /api/v1/erp/");
        // Gateway forwards /api/v1/erp/** → FallbackController at /fallback/erp/**
        String fallbackPath = "/fallback/erp/" + examplePath.substring("/api/v1/erp/".length());
        assertErpHttp501(fallbackPath);
    }

    private static void assertErpHttp501(String fallbackPath) {
        erpFallbackClient.get()
                .uri(fallbackPath)
                .exchange()
                .expectStatus().isEqualTo(501)
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.service").isEqualTo("erp-adapter-service")
                .jsonPath("$.message").value(message ->
                        assertTrue(
                                message.toString().contains(ERP_501_MESSAGE_FRAGMENT),
                                "ERP stub must advertise reserved agent-contract message"))
                .jsonPath("$.timestamp").isNotEmpty()
                .consumeWith(result -> {
                    byte[] body = result.getResponseBody();
                    assertNotNull(body, "ERP 501 body must be present");
                    try {
                        JsonNode json = MAPPER.readTree(body);
                        assertEquals(
                                ERP_501_JSON_FIELDS,
                                Set.copyOf(iterableToList(json.fieldNames())),
                                "ERP 501 JSON must match documented fields exactly");
                    } catch (Exception e) {
                        throw new AssertionError("ERP 501 body must be valid JSON", e);
                    }
                });
    }

    private static List<String> iterableToList(java.util.Iterator<String> iterator) {
        List<String> names = new ArrayList<>();
        iterator.forEachRemaining(names::add);
        return names;
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
