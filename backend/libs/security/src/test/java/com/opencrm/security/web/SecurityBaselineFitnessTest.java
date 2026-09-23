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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-016 fitness functions over the sources and configuration of every service in {@code backend/services}.
 * <ul>
 *   <li>decision 4: the servlet chain comes from {@code libs/security}; services add rules through
 *       {@link OpenCrmSecurityCustomizer} and declare no {@code SecurityFilterChain} or {@code @EnableWebSecurity};</li>
 *   <li>decision 4: no {@code permitAll()} on {@code /internal/**}, and the transitional
 *       {@code opencrm.security.legacy-open-internal} only where allowlisted;</li>
 *   <li>decision 4: no committed configuration selects {@code opencrm.security.mode: dev}; dev mode comes from the
 *       {@code dev} profile only;</li>
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
     * Services that keep {@code /internal/**} open until their callers send service tokens. ADR-016 rollout step 2
     * empties this map; the test fails while an entry no longer sets the flag, so the map cannot go stale.
     */
    private static final Map<String, String> LEGACY_OPEN_INTERNAL_ALLOWLIST = Map.of(
            "pricebook-service", "pricing-service price-book lookups (/internal/v1/price-books)",
            "configuration-engine", "quote-service validation through the gateway rewrite (/internal/v1/configurations)");

    private static final Pattern OWN_CHAIN = Pattern.compile(
            "\\bSecurityFilterChain\\b|@EnableWebSecurity\\b|WebSecurityConfigurerAdapter|\\.oauth2ResourceServer\\s*\\(");
    private static final Pattern INTERNAL_PERMIT_ALL = Pattern.compile(
            "\"/internal[^\"]*\"\\s*\\)\\s*\\.permitAll\\s*\\(");

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
    @DisplayName("legacy-open-internal is set exactly by the allowlisted services, until step 2")
    void legacyOpenInternalOnlyWhereAllowlisted() {
        Set<String> setting = new TreeSet<>();
        for (ServiceSources service : services) {
            for (Map<String, Object> document : service.configDocuments()) {
                if (Boolean.TRUE.equals(securityProperty(document, "legacy-open-internal"))) {
                    setting.add(service.name());
                }
            }
        }
        assertThat(setting).isEqualTo(new TreeSet<>(LEGACY_OPEN_INTERNAL_ALLOWLIST.keySet()));
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
                Path resources = serviceDir.resolve("src/main/resources");
                if (Files.isDirectory(resources)) {
                    try (Stream<Path> files = Files.list(resources)) {
                        for (Path file : files.filter(p -> p.getFileName().toString().matches("application.*\\.ya?ml"))
                                .filter(p -> !p.getFileName().toString().startsWith("application-dev."))
                                .toList()) {
                            for (Object document : new Yaml().loadAll(Files.readString(file, StandardCharsets.UTF_8))) {
                                if (document instanceof Map<?, ?> map) {
                                    documents.add((Map<String, Object>) map);
                                }
                            }
                        }
                    }
                }
                result.add(new ServiceSources(serviceDir.getFileName().toString(),
                        Files.readString(pom, StandardCharsets.UTF_8), javaFiles, documents));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record ServiceSources(String name, String pom, Map<String, String> javaFiles,
                                  List<Map<String, Object>> configDocuments) {
    }
}
