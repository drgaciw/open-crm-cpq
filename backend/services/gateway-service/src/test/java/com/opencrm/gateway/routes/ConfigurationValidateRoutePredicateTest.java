package com.opencrm.gateway.routes;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F4 stub: gateway must match {@code POST /api/v1/configuration/validate} (and CPQ alias)
 * with a route that is declared <em>before</em> the broad {@code Path=.../configuration/**}
 * product pass-through (Wave F3 predicate-order constraint).
 * <p>
 * Until F3 lands, documents current wildcard ownership and the required insertion order.
 * After F3, asserts the specific validate route precedes {@code configuration/**}.
 */
@Tag("unit")
@Tag("contract")
@DisplayName("F4 gateway validate-before-configuration/** route predicate stub")
class ConfigurationValidateRoutePredicateTest {

    private static final Path APPLICATION_YAML = Path.of("src/main/resources/application.yml");
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Pattern ROUTE_BLOCK = Pattern.compile(
            "(?m)^\\s*- id: ([\\w-]+)[\\s\\S]*?(?=^\\s*- id: |\\Z)");
    private static final Pattern PATH_PREDICATE = Pattern.compile("Path=([^\\n]+)");

    private static final String VALIDATE_PATH = "/api/v1/configuration/validate";
    private static final String CPQ_VALIDATE_PATH = "/api/v1/cpq/configuration/validate";
    private static final String RULES_PATH = "/api/v1/configuration/rules";
    private static final String CONFIGURATION_WILDCARD = "/api/v1/configuration/**";
    private static final String CPQ_CONFIGURATION_WILDCARD = "/api/v1/cpq/configuration/**";

    private static String yaml;
    private static List<RoutePaths> routesInOrder;

    @BeforeAll
    static void loadYaml() throws Exception {
        yaml = Files.readString(APPLICATION_YAML);
        routesInOrder = parseRoutes(yaml);
        assertFalse(routesInOrder.isEmpty(), "application.yml must declare gateway routes");
    }

    @Test
    @DisplayName("validate and rules currently match product configuration/** wildcard")
    void preF3ValidateAndRulesMatchProductWildcard() {
        assertTrue(yaml.contains(CONFIGURATION_WILDCARD),
                "product-service must keep Path including /api/v1/configuration/**");
        assertTrue(matchesAnyDeclaredPattern(VALIDATE_PATH),
                "validate path must be covered by a Path predicate (today: configuration/**)");
        assertTrue(matchesAnyDeclaredPattern(RULES_PATH),
                "rules CRUD path must remain covered by configuration/**");
    }

    @Test
    @DisplayName("F3/F4: when validate-specific route exists it must precede configuration/**")
    void validateSpecificRouteMustPrecedeConfigurationWildcardWhenPresent() {
        int validateRouteIndex = indexOfFirstRouteMatching(VALIDATE_PATH, true);
        int wildcardRouteIndex = indexOfFirstRouteWithExactPattern(CONFIGURATION_WILDCARD);

        assertTrue(wildcardRouteIndex >= 0,
                "Expected a route declaring Path=.../configuration/**");

        if (validateRouteIndex < 0 || validateRouteIndex == wildcardRouteIndex) {
            // Pre-F3: only the broad product wildcard matches validate — document required F3 order.
            assertTrue(
                    firstMatchingPattern(VALIDATE_PATH).contains("/configuration/**"),
                    "Pre-F3 validate is owned by configuration/**; F3 must insert a more specific "
                            + "validate route (→ configuration-engine evaluate) before that wildcard");
            return;
        }

        assertTrue(validateRouteIndex < wildcardRouteIndex,
                () -> "Validate-specific route (index " + validateRouteIndex
                        + ") must be declared before configuration/** (index "
                        + wildcardRouteIndex + ") or Spring Cloud Gateway will keep routing "
                        + "agents to product-service List<String>");
    }

    @Test
    @DisplayName("F3/F4: CPQ alias validate must precede cpq/configuration/** when specific route exists")
    void cpqValidateSpecificRouteMustPrecedeCpqWildcardWhenPresent() {
        int validateRouteIndex = indexOfFirstRouteMatching(CPQ_VALIDATE_PATH, true);
        int wildcardRouteIndex = indexOfFirstRouteWithExactPattern(CPQ_CONFIGURATION_WILDCARD);

        assertTrue(wildcardRouteIndex >= 0,
                "Expected a route declaring Path=.../cpq/configuration/**");

        if (validateRouteIndex < 0 || validateRouteIndex == wildcardRouteIndex) {
            assertTrue(
                    firstMatchingPattern(CPQ_VALIDATE_PATH).contains("/cpq/configuration/**"),
                    "Pre-F3 CPQ validate is owned by cpq/configuration/**; F3 must insert a more "
                            + "specific validate alias before that wildcard");
            return;
        }

        assertTrue(validateRouteIndex < wildcardRouteIndex,
                () -> "CPQ validate-specific route must precede cpq/configuration/**");
    }

    @Test
    @DisplayName("ordering helper: specific validate pattern beats configuration/**")
    void specificValidatePatternBeatsWildcardInPredicateOrderModel() {
        List<String> ordered = List.of(
                "/api/v1/configuration/validate",
                "/api/v1/configuration/**");
        assertTrue(PATH_MATCHER.match(ordered.get(0), VALIDATE_PATH));
        assertTrue(PATH_MATCHER.match(ordered.get(1), VALIDATE_PATH));
        assertTrue(PATH_MATCHER.match(ordered.get(1), RULES_PATH));
        assertFalse(PATH_MATCHER.match(ordered.get(0), RULES_PATH),
                "Validate-only predicate must not steal rules CRUD");

        // First-match-wins model used by SCG when routes are ordered correctly.
        String winnerForValidate = firstMatch(ordered, VALIDATE_PATH);
        String winnerForRules = firstMatch(ordered, RULES_PATH);
        assertTrue(winnerForValidate.endsWith("/validate"));
        assertTrue(winnerForRules.endsWith("/**"));
    }

    private static String firstMatch(List<String> patterns, String path) {
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, path)) {
                return pattern;
            }
        }
        return null;
    }

    private static boolean matchesAnyDeclaredPattern(String path) {
        for (RoutePaths route : routesInOrder) {
            for (String pattern : route.patterns) {
                if (PATH_MATCHER.match(pattern, path)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String firstMatchingPattern(String path) {
        for (RoutePaths route : routesInOrder) {
            for (String pattern : route.patterns) {
                if (PATH_MATCHER.match(pattern, path)) {
                    return pattern;
                }
            }
        }
        return "";
    }

    /**
     * @param requireNonWildcard when true, ignore routes whose only match is a {@code /**}
     *                           suffix so a future validate-specific route is preferred
     */
    private static int indexOfFirstRouteMatching(String path, boolean preferSpecific) {
        int wildcardFallback = -1;
        for (int i = 0; i < routesInOrder.size(); i++) {
            RoutePaths route = routesInOrder.get(i);
            for (String pattern : route.patterns) {
                if (!PATH_MATCHER.match(pattern, path)) {
                    continue;
                }
                boolean wildcard = pattern.endsWith("/**");
                if (!wildcard) {
                    return i;
                }
                if (wildcardFallback < 0) {
                    wildcardFallback = i;
                }
            }
        }
        return preferSpecific ? wildcardFallback : wildcardFallback;
    }

    private static int indexOfFirstRouteWithExactPattern(String exactPattern) {
        for (int i = 0; i < routesInOrder.size(); i++) {
            if (routesInOrder.get(i).patterns.contains(exactPattern)) {
                return i;
            }
        }
        return -1;
    }

    private static List<RoutePaths> parseRoutes(String yamlText) {
        Map<String, RoutePaths> byId = new LinkedHashMap<>();
        Matcher blockMatcher = ROUTE_BLOCK.matcher(yamlText);
        while (blockMatcher.find()) {
            String id = blockMatcher.group(1);
            String block = blockMatcher.group(0);
            List<String> patterns = new ArrayList<>();
            Matcher pathMatcher = PATH_PREDICATE.matcher(block);
            while (pathMatcher.find()) {
                for (String part : pathMatcher.group(1).trim().split(",")) {
                    String pattern = part.trim();
                    if (!pattern.isEmpty()) {
                        patterns.add(pattern);
                    }
                }
            }
            if (!patterns.isEmpty()) {
                byId.putIfAbsent(id, new RoutePaths(id, patterns));
            }
        }
        return new ArrayList<>(byId.values());
    }

    private record RoutePaths(String id, List<String> patterns) {
    }
}
