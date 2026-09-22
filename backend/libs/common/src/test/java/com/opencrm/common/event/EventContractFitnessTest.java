package com.opencrm.common.event;

import com.opencrm.common.event.EventContractRegistry.EventContract;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-018 D5 and D9 fitness functions, run over the production sources of every service in {@code backend/services}.
 *
 * <p>The compiler already guarantees that every outbox publish names a registry contract, because
 * {@link OutboxEventPublisher#publish} takes an {@link EventContract}. This test checks the other direction and the
 * single-emitter rule:
 * <ul>
 *   <li>every PUBLISHED contract has exactly one publish call site, in the service that owns it (D5 "has a producer",
 *       D9 "one emitter");</li>
 *   <li>a RESERVED contract has none;</li>
 *   <li>domain events leave a service only through the outbox relay: no {@code KafkaTemplate} in services, and Redis
 *       {@code convertAndSend} only where allowlisted.</li>
 * </ul>
 *
 * <p>It reads source text rather than bytecode, because topic and contract constants are inlined by the compiler. Each
 * publish call must therefore name its contract as {@code EventContractRegistry.X} in the argument list; a static
 * import or a contract passed through a variable is reported, not guessed at.
 */
@DisplayName("ADR-018 D5/D9: the event registry is the contract, one emitter per event")
class EventContractFitnessTest {

    private static final Pattern PUBLISH_CALL = Pattern.compile("\\.publish\\s*\\(");
    private static final Pattern CONTRACT_REF = Pattern.compile("EventContractRegistry\\s*\\.\\s*([A-Z][A-Z0-9_]*)");
    private static final Pattern STATIC_REGISTRY_IMPORT =
            Pattern.compile("import\\s+static\\s+com\\.opencrm\\.common\\.event\\.EventContractRegistry\\b");

    /**
     * Redis publishes allowed during a migration, with the reason. Remove an entry when its file goes: the test fails
     * while an entry points at a file that no longer exists.
     */
    private static final Map<String, String> REDIS_PUBLISH_ALLOWLIST = Map.of(
            "quote-service/src/main/java/com/opencrm/quote/event/QuoteEventPublisher.java",
            "ADR-018 D8 dual run: Redis quote:accepted until transition step 3 removes it");

    private static Path servicesDir;
    private static List<SourceFile> sources;
    private static List<CallSite> callSites;
    private static List<String> malformedCalls;

    @BeforeAll
    static void scanServices() {
        servicesDir = locateServicesDir();
        sources = readProductionSources(servicesDir);
        callSites = new ArrayList<>();
        malformedCalls = new ArrayList<>();
        for (SourceFile source : sources) {
            if (source.text().contains("OutboxEventPublisher")) {
                collectPublishCalls(source);
            }
        }
    }

    @Test
    @DisplayName("the scan sees the services and their publish calls")
    void scanFindsServicesAndPublishCalls() {
        assertThat(sources).as("production sources under %s", servicesDir).hasSizeGreaterThan(100);
        assertThat(callSites).as("outbox publish call sites").isNotEmpty();
    }

    @Test
    @DisplayName("every outbox publish names exactly one registry contract, directly")
    void everyPublishNamesOneRegistryContract() {
        List<String> staticImports = sources.stream()
                .filter(source -> STATIC_REGISTRY_IMPORT.matcher(source.text()).find())
                .map(SourceFile::relativePath)
                .toList();

        assertThat(malformedCalls)
                .as("publish calls must pass EventContractRegistry.<CONTRACT> in their argument list")
                .isEmpty();
        assertThat(staticImports)
                .as("static imports from EventContractRegistry hide publish calls from this check")
                .isEmpty();
    }

    @Test
    @DisplayName("every PUBLISHED contract has exactly one publish call site, in its owning service (D5, D9)")
    void everyPublishedContractHasOneEmitterInItsOwner() {
        Map<String, List<CallSite>> sitesByContract = callSites.stream()
                .collect(Collectors.groupingBy(CallSite::contractName, TreeMap::new, Collectors.toList()));

        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, EventContract> entry : registeredContracts().entrySet()) {
            EventContract contract = entry.getValue();
            if (!contract.isPublished()) {
                continue;
            }
            List<CallSite> sites = sitesByContract.getOrDefault(entry.getKey(), List.of());
            if (sites.size() != 1) {
                violations.add(entry.getKey() + " (" + contract.topic() + ") has " + sites.size()
                        + " publish call sites, expected exactly 1: " + sites);
            }
            for (CallSite site : sites) {
                if (!site.service().equals(contract.owner())) {
                    violations.add(entry.getKey() + " is owned by " + contract.owner() + " but published at " + site);
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("RESERVED contracts have no publish call site, and every call names a registered contract")
    void reservedContractsAreNotPublished() {
        Map<String, EventContract> registered = registeredContracts();
        List<String> violations = new ArrayList<>();
        for (CallSite site : callSites) {
            EventContract contract = registered.get(site.contractName());
            if (contract == null) {
                violations.add(site + " names EventContractRegistry." + site.contractName()
                        + ", which is not an EventContract constant");
            } else if (!contract.isPublished()) {
                violations.add(site + " publishes RESERVED " + site.contractName()
                        + "; switch it to PUBLISHED with its payload class first");
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("domain events leave a service only through the outbox relay")
    void domainEventsLeaveOnlyThroughTheOutboxRelay() {
        List<String> kafkaSenders = sources.stream()
                .filter(source -> source.text().contains("KafkaTemplate"))
                .map(SourceFile::relativePath)
                .toList();
        List<String> redisPublishers = sources.stream()
                .filter(source -> source.text().contains("convertAndSend"))
                .map(SourceFile::relativePath)
                .filter(path -> !REDIS_PUBLISH_ALLOWLIST.containsKey(path))
                .toList();
        List<String> staleAllowlist = REDIS_PUBLISH_ALLOWLIST.keySet().stream()
                .filter(path -> !Files.exists(servicesDir.resolve(path)))
                .toList();

        assertThat(kafkaSenders)
                .as("services must publish through OutboxEventPublisher; only the libs/common relay sends to Kafka")
                .isEmpty();
        assertThat(redisPublishers).as("Redis publishes outside REDIS_PUBLISH_ALLOWLIST").isEmpty();
        assertThat(staleAllowlist).as("REDIS_PUBLISH_ALLOWLIST entries whose file is gone; remove them").isEmpty();
    }

    // --- scanning ---------------------------------------------------------------------------------------------------

    private static void collectPublishCalls(SourceFile source) {
        String text = source.text();
        Matcher call = PUBLISH_CALL.matcher(text);
        while (call.find()) {
            int open = call.end() - 1;
            String arguments = argumentsOf(text, open);
            int line = lineOf(text, call.start());
            List<String> contracts = new ArrayList<>();
            Matcher ref = CONTRACT_REF.matcher(arguments);
            while (ref.find()) {
                contracts.add(ref.group(1));
            }
            String location = source.relativePath() + ":" + line;
            if (contracts.size() != 1) {
                malformedCalls.add(location + " names " + contracts.size() + " registry contracts: "
                        + arguments.replaceAll("\\s+", " ").trim());
            } else {
                callSites.add(new CallSite(contracts.get(0), source.service(), location));
            }
        }
    }

    /** The text between the parenthesis at {@code open} and its match, ignoring parentheses in string literals. */
    private static String argumentsOf(String text, int open) {
        int depth = 0;
        boolean inString = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return text.substring(open + 1, i);
            }
        }
        throw new IllegalStateException("Unbalanced parentheses after offset " + open);
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static Map<String, EventContract> registeredContracts() {
        Map<String, EventContract> contracts = new TreeMap<>();
        for (Field field : EventContractRegistry.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == EventContract.class) {
                try {
                    contracts.put(field.getName(), (EventContract) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return contracts;
    }

    private static Path locateServicesDir() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path services = dir.resolve("services");
            if (Files.isDirectory(services) && Files.isDirectory(dir.resolve("libs").resolve("common"))) {
                return services;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("backend/services not found above " + Path.of("").toAbsolutePath());
    }

    private static List<SourceFile> readProductionSources(Path services) {
        try (Stream<Path> serviceDirs = Files.list(services)) {
            List<SourceFile> result = new ArrayList<>();
            for (Path serviceDir : serviceDirs.filter(Files::isDirectory).sorted().toList()) {
                Path main = serviceDir.resolve("src").resolve("main").resolve("java");
                if (!Files.isDirectory(main)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(main)) {
                    for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                        String relative = services.relativize(file).toString().replace('\\', '/');
                        result.add(new SourceFile(serviceDir.getFileName().toString(), relative,
                                Files.readString(file, StandardCharsets.UTF_8)));
                    }
                }
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record SourceFile(String service, String relativePath, String text) {
    }

    private record CallSite(String contractName, String service, String location) {
        @Override
        public String toString() {
            return location;
        }
    }
}
