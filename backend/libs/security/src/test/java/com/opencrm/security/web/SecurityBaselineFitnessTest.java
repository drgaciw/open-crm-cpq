package com.opencrm.security.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-016 fitness functions over the sources and configuration of every service in {@code backend/services}.
 * <ul>
 *   <li>decision 4: the servlet chain comes from {@code libs/security}; services add rules through
 *       {@link OpenCrmSecurityCustomizer} and declare no {@code SecurityFilterChain} or {@code @EnableWebSecurity};</li>
 *   <li>decision 4: no {@code permitAll()} on {@code /internal/**}; the step-1 {@code legacy-open-internal} flag is
 *       gone; the gateway never routes onto {@code /internal/**}; no gRPC listener is on by default, because it would
 *       bypass the chain;</li>
 *   <li>decision 4: no committed configuration selects {@code opencrm.security.mode: dev}; dev mode comes from the
 *       {@code dev} profile only;</li>
 *   <li>decision 5: every HTTP client a service builds takes an {@code OutboundAuth} interceptor, unless it calls an
 *       external API (allowlisted); services do not call the gateway (allowlisted: the ai-service agent tools);</li>
 *   <li>decision 9: every servlet service depends on {@code libs/security}, so none runs without the baseline.</li>
 * </ul>
 * It reads source text, like {@code EventContractFitnessTest}: the rules are about what is written, not about beans.
 */
@DisplayName("ADR-016 fitness: one baseline chain, /internal never open")
class SecurityBaselineFitnessTest {

    /** Services whose security is not the servlet baseline, with the reason. */
    private static final Map<String, String> OWN_CHAIN_ALLOWLIST = Map.of(
            "gateway-service", "reactive edge (WebFlux); becomes the BFF in ADR-016 rollout step 3");

    /**
     * Source files that build an HTTP client for an API outside the OpenCRM trust domain, which authenticates with its
     * own credentials. The test fails while an entry no longer builds a client, so the map cannot go stale.
     */
    private static final Map<String, String> EXTERNAL_CLIENT_ALLOWLIST = Map.of(
            "ai-service/src/main/java/com/opencrm/ai/selection/execution/OpenRouterExecutionClient.java",
            "OpenRouter inference, authenticated with its own API key",
            "ai-service/src/main/java/com/opencrm/ai/selection/evaluation/SelectionEvaluationCli.java",
            "offline model-selection evaluation CLI against external APIs");

    /**
     * Services that call the gateway, with the reason. Decision 5: domain services never call the gateway. The
     * ai-service agent tools use the gateway's agent contract, like external agents do (F3). Rollout step 3 kept that:
     * the gateway's bearer lane validates the relayed token and forwards it unchanged.
     */
    private static final Map<String, String> GATEWAY_CALLER_ALLOWLIST = Map.of(
            "ai-service", "agent tools on the gateway's bearer lane (F3 agent contract), user token relayed (step 3)");

    private static final Pattern OWN_CHAIN = Pattern.compile(
            "\\bSecurityFilterChain\\b|@EnableWebSecurity\\b|WebSecurityConfigurerAdapter|\\.oauth2ResourceServer\\s*\\(");
    private static final Pattern INTERNAL_PERMIT_ALL = Pattern.compile(
            "\"/internal[^\"]*\"\\s*\\)\\s*\\.permitAll\\s*\\(");
    private static final Pattern CLIENT_CONSTRUCTION = Pattern.compile(
            "RestClient\\.builder\\s*\\(|RestClient\\.create\\s*\\(|WebClient\\.builder\\s*\\(|WebClient\\.create\\s*\\("
                    + "|new\\s+RestTemplate\\s*\\(|HttpClient\\.new(Builder|HttpClient)\\s*\\(|RestClient\\.Builder\\s+\\w+");
    private static final Pattern GATEWAY_URL_KEY = Pattern.compile("services\\.gateway|opencrm\\.gateway|GATEWAY_SERVICE_URL");
    private static final Pattern ROUTE_ONTO_INTERNAL = Pattern.compile("(?m)^[^#\\n]*(RewritePath|Path)=[^\\n]*/internal");
    private static final Pattern GRPC_PORT_OFF_BY_DEFAULT = Pattern.compile("^\\$\\{[A-Za-z0-9_.]+:-1}$");
    private static final Pattern IDENTITY_HEADER_FILTER = Pattern.compile(
            "(?mi)^[^#\\n]*(AddRequestHeader|SetRequestHeader|MapRequestHeader)=\\s*(X-User|X-Actor|X-Auth|Authorization)[^\\n]*");

    private static Path servicesDir;
    private static List<ServiceSources> services;

    @BeforeAll
    static void scan() {
        servicesDir = locateServicesDir();
        services = readServices(servicesDir);
    }

    @Test
    @DisplayName("the scan sees every service")
    void scanSeesServices() {
        assertThat(services).extracting(ServiceSources::name)
                .contains("quote-service", "pricebook-service", "configuration-engine", "ai-service", "gateway-service")
                .hasSizeGreaterThanOrEqualTo(11);
        assertThat(services.stream().mapToInt(s -> s.javaFiles().size()).sum()).isGreaterThan(100);
    }

    @Test
    @DisplayName("no service declares its own servlet SecurityFilterChain or @EnableWebSecurity (D4)")
    void noOwnChains() {
        List<String> violations = new ArrayList<>();
        for (ServiceSources service : services) {
            if (OWN_CHAIN_ALLOWLIST.containsKey(service.name())) {
                continue;
            }
            service.javaFiles().forEach((path, text) -> {
                if (OWN_CHAIN.matcher(text).find()) {
                    violations.add(path);
                }
            });
        }
        assertThat(violations)
                .as("add rules with an OpenCrmSecurityCustomizer bean instead of a chain (ADR-016 D4)")
                .isEmpty();
    }

    @Test
    @DisplayName("no permitAll() on /internal/** in any service (D4)")
    void noInternalPermitAll() {
        List<String> violations = new ArrayList<>();
        for (ServiceSources service : services) {
            service.javaFiles().forEach((path, text) -> {
                if (INTERNAL_PERMIT_ALL.matcher(text).find()) {
                    violations.add(path);
                }
            });
        }
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("no service sets the step-1 legacy-open-internal flag, which step 2 removed")
    void noLegacyOpenInternal() {
        Set<String> setting = new TreeSet<>();
        for (ServiceSources service : services) {
            for (Map<String, Object> document : service.configDocuments()) {
                if (securityProperty(document, "legacy-open-internal") != null) {
                    setting.add(service.name());
                }
            }
        }
        assertThat(setting).as("/internal/** needs a service token; the flag no longer exists").isEmpty();
    }

    @Test
    @DisplayName("every HTTP client a service builds takes an OutboundAuth interceptor, unless external (D5)")
    void everyClientAuthenticates() {
        List<String> unauthenticated = new ArrayList<>();
        Set<String> buildingClients = new TreeSet<>();
        for (ServiceSources service : services) {
            service.javaFiles().forEach((path, text) -> {
                if (!CLIENT_CONSTRUCTION.matcher(text).find()) {
                    return;
                }
                buildingClients.add(path);
                if (!EXTERNAL_CLIENT_ALLOWLIST.containsKey(path) && !text.contains("OutboundAuth")) {
                    unauthenticated.add(path);
                }
            });
        }
        assertThat(unauthenticated)
                .as("add outboundAuth.relayUserToken() for /api/v1 calls or outboundAuth.serviceToken() for /internal")
                .isEmpty();
        assertThat(buildingClients).as("stale EXTERNAL_CLIENT_ALLOWLIST entries")
                .containsAll(EXTERNAL_CLIENT_ALLOWLIST.keySet());
        assertThat(buildingClients).as("the scan finds the known service clients")
                .contains("quote-service/src/main/java/com/opencrm/quote/config/RestClientConfig.java",
                        "pricing-service/src/main/java/com/opencrm/pricing/config/PriceBookClientConfig.java");
    }

    @Test
    @DisplayName("services do not call the gateway, except the allowlisted agent tools (D5)")
    void servicesDoNotCallTheGateway() {
        Set<String> callers = new TreeSet<>();
        for (ServiceSources service : services) {
            if (service.name().equals("gateway-service")) {
                continue;
            }
            boolean inConfig = GATEWAY_URL_KEY.matcher(service.configText()).find();
            boolean inCode = service.javaFiles().values().stream().anyMatch(t -> GATEWAY_URL_KEY.matcher(t).find());
            if (inConfig || inCode) {
                callers.add(service.name());
            }
        }
        assertThat(callers).isEqualTo(new TreeSet<>(GATEWAY_CALLER_ALLOWLIST.keySet()));
    }

    @Test
    @DisplayName("the gateway never routes browser traffic onto /internal/** (D3, D4)")
    void gatewayNeverRoutesOntoInternal() {
        ServiceSources gateway = services.stream().filter(s -> s.name().equals("gateway-service")).findFirst()
                .orElseThrow();
        assertThat(gateway.configText()).as("gateway route config").isNotBlank();
        Matcher matcher = ROUTE_ONTO_INTERNAL.matcher(gateway.configText());
        List<String> routes = new ArrayList<>();
        while (matcher.find()) {
            routes.add(matcher.group().trim());
        }
        assertThat(routes).as("route to the service's public /api/v1 path instead").isEmpty();
    }

    @Test
    @DisplayName("the gateway trusts one tenant: no multi-tenant issuer, no Azure AD B2C (D1, D8)")
    void gatewayTrustsOneTenant() {
        ServiceSources gateway = services.stream().filter(s -> s.name().equals("gateway-service")).findFirst()
                .orElseThrow();
        String config = gateway.configText().toLowerCase(java.util.Locale.ROOT);
        assertThat(config).doesNotContainPattern("://[a-z0-9.-]+/(common|organizations|consumers)\\b")
                .doesNotContain("b2c");
        assertThat(gateway.pom()).doesNotContain("active-directory-b2c");
    }

    @Test
    @DisplayName("gateway routes add no identity headers; identity is relayed only as a validated token (D3)")
    void gatewayRoutesAddNoIdentityHeaders() {
        ServiceSources gateway = services.stream().filter(s -> s.name().equals("gateway-service")).findFirst()
                .orElseThrow();
        Matcher matcher = IDENTITY_HEADER_FILTER.matcher(gateway.configText());
        List<String> filters = new ArrayList<>();
        while (matcher.find()) {
            filters.add(matcher.group().trim());
        }
        assertThat(filters).isEmpty();
        assertThat(gateway.javaFiles().keySet())
                .contains("gateway-service/src/main/java/com/opencrm/gateway/security/IdentityRelayFilter.java");
    }

    @Test
    @DisplayName("no gRPC listener is on by default: it would bypass the servlet chain (D4)")
    void grpcServersOffByDefault() {
        List<String> violations = new ArrayList<>();
        for (ServiceSources service : services) {
            for (Map<String, Object> document : service.configDocuments()) {
                Object port = nested(document, "grpc", "server", "port");
                if (port == null) {
                    continue;
                }
                String value = port.toString().trim();
                if (!value.equals("-1") && !GRPC_PORT_OFF_BY_DEFAULT.matcher(value).matches()) {
                    violations.add(service.name() + ": grpc.server.port=" + value);
                }
            }
        }
        assertThat(violations).as("use grpc.server.port: ${<ENV>:-1} until the listener authenticates").isEmpty();
    }

    @Test
    @DisplayName("no committed configuration selects opencrm.security.mode=dev (D4)")
    void noCommittedDevMode() {
        List<String> violations = new ArrayList<>();
        for (ServiceSources service : services) {
            for (Map<String, Object> document : service.configDocuments()) {
                Object mode = securityProperty(document, "mode");
                if (mode != null && "dev".equalsIgnoreCase(mode.toString().trim())) {
                    violations.add(service.name());
                }
            }
        }
        assertThat(violations).as("dev mode comes from the dev profile, never from committed config").isEmpty();
    }

    @Test
    @DisplayName("every servlet service depends on libs/security (D9: no service without the baseline)")
    void everyServiceUsesTheBaseline() {
        List<String> missing = services.stream()
                .filter(service -> !OWN_CHAIN_ALLOWLIST.containsKey(service.name()))
                .filter(service -> !service.pom().contains("<artifactId>security</artifactId>"))
                .map(ServiceSources::name)
                .toList();
        assertThat(missing).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Object securityProperty(Map<String, Object> document, String key) {
        Object flat = document.get("opencrm.security." + key);
        if (flat != null) {
            return flat;
        }
        Object opencrm = document.get("opencrm");
        if (!(opencrm instanceof Map<?, ?> opencrmMap)) {
            return null;
        }
        Object security = opencrmMap.get("security");
        if (security instanceof Map<?, ?> securityMap) {
            return ((Map<String, Object>) securityMap).get(key);
        }
        return opencrmMap.get("security." + key);
    }

    /** {@code a.b.c} from nested maps, or the flat key {@code "a.b.c"}; null when absent. */
    private static Object nested(Map<String, Object> document, String... keys) {
        Object flat = document.get(String.join(".", keys));
        if (flat != null) {
            return flat;
        }
        Object current = document;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }

    private static Path locateServicesDir() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("services");
            if (Files.isDirectory(candidate) && Files.isDirectory(dir.resolve("libs").resolve("security"))) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("backend/services not found above " + Path.of("").toAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static List<ServiceSources> readServices(Path servicesDir) {
        try (Stream<Path> dirs = Files.list(servicesDir)) {
            List<ServiceSources> result = new ArrayList<>();
            for (Path serviceDir : dirs.filter(Files::isDirectory).sorted().toList()) {
                Path pom = serviceDir.resolve("pom.xml");
                if (!Files.isRegularFile(pom)) {
                    continue;
                }
                Map<String, String> javaFiles = new java.util.TreeMap<>();
                Path main = serviceDir.resolve("src/main/java");
                if (Files.isDirectory(main)) {
                    try (Stream<Path> files = Files.walk(main)) {
                        for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                            javaFiles.put(servicesDir.relativize(file).toString().replace('\\', '/'),
                                    Files.readString(file, StandardCharsets.UTF_8));
                        }
                    }
                }
                List<Map<String, Object>> documents = new ArrayList<>();
                StringBuilder configText = new StringBuilder();
                Path resources = serviceDir.resolve("src/main/resources");
                if (Files.isDirectory(resources)) {
                    try (Stream<Path> files = Files.list(resources)) {
                        for (Path file : files.filter(p -> p.getFileName().toString().matches("application.*\\.ya?ml"))
                                .filter(p -> !p.getFileName().toString().startsWith("application-dev."))
                                .sorted()
                                .toList()) {
                            String text = Files.readString(file, StandardCharsets.UTF_8);
                            configText.append(text).append('\n');
                            for (Object document : new Yaml().loadAll(text)) {
                                if (document instanceof Map<?, ?> map) {
                                    documents.add((Map<String, Object>) map);
                                }
                            }
                        }
                    }
                }
                result.add(new ServiceSources(serviceDir.getFileName().toString(),
                        Files.readString(pom, StandardCharsets.UTF_8), javaFiles, documents, configText.toString()));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record ServiceSources(String name, String pom, Map<String, String> javaFiles,
                                  List<Map<String, Object>> configDocuments, String configText) {
    }
}
