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
import java.util.HashMap;
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
 *   <li>decision 6: actors come from the verified token. Request parameters, headers and bodies cannot supply an
 *       actor, and no code records a hard-coded actor such as {@code "system"};</li>
 *   <li>decision 9: every servlet service depends on {@code libs/security}, so none runs without the baseline;</li>
 *   <li>owner decision 4: every service ships the Prometheus registry and exposes only {@code health}, {@code info}
 *       and {@code prometheus} (the gateway also {@code gateway}, read-only);</li>
 *   <li>step 5 go-live gate: no secret in committed configuration. A secret-named key outside the local profiles is a
 *       placeholder with no default, or an empty one;</li>
 *   <li>step 5 go-live gate: {@code infra/entra/opencrm-identity.json}, the Entra configuration to apply, agrees with
 *       the code: the app roles the code checks, v2.0 tokens, the {@code idtyp} claim, the gateway's redirect path and
 *       a workload identity for every service that calls another.</li>
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

    /**
     * The field registry retains actor-named inputs solely to reject non-null values; its service tests exercise
     * every mutation and nested option. The value request's userId is a USER-type field value, not the audit actor.
     * Keep this exception bound to exact files and names; all other request inputs still fail this fitness check.
     */
    private static final Set<String> REJECTED_FIELD_ACTORS = Set.of("createdBy", "updatedBy", "actor", "archivedBy");
    private static final Map<String, Set<String>> REQUEST_BODY_IDENTITY_EXCEPTIONS = Map.of(
            "crm-service/src/main/java/com/opencrm/crm/fields/FieldArchiveRequest.java", REJECTED_FIELD_ACTORS,
            "crm-service/src/main/java/com/opencrm/crm/fields/FieldDefinitionCreateRequest.java", REJECTED_FIELD_ACTORS,
            "crm-service/src/main/java/com/opencrm/crm/fields/FieldDefinitionUpdateRequest.java", REJECTED_FIELD_ACTORS,
            "crm-service/src/main/java/com/opencrm/crm/fields/FieldOptionWriteRequest.java", REJECTED_FIELD_ACTORS,
            "crm-service/src/main/java/com/opencrm/crm/fields/FieldValueWriteRequest.java",
            Set.of("createdBy", "updatedBy", "actor", "archivedBy", "userId"));

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
    /** Names that say who acted. Decision 6: they come from the token, never from the request. */
    private static final Set<String> IDENTITY_NAMES = Set.of("approver", "approverid", "approvedby", "requester",
            "requesterid", "requestedby", "actor", "actorid", "decidedby", "acceptedby", "rejectedby", "userid",
            "createdby", "updatedby", "archivedby", "generatedby", "changedby", "submittedby", "performedby");
    private static final Pattern REQUEST_INPUT = Pattern.compile(
            "@Request(?:Param|Header)\\b(\\s*\\(([^)]*)\\))?\\s+(?:final\\s+)?[\\w.<>?]+\\s+(\\w+)\\s*[,)]");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern FIELD = Pattern.compile(
            "(?m)^\\s*(?:private|protected|public)?\\s*(?:final\\s+)?[\\w.<>?, ]+\\s+(\\w+)\\s*(?:=[^;]*)?;");
    private static final Pattern RECORD_COMPONENTS = Pattern.compile("\\brecord\\s+\\w+\\s*\\(([^)]*)\\)");
    private static final Pattern LITERAL_ACTOR = Pattern.compile("\\.(?:set)?(?i:requesterId|approverId|approver"
            + "|requestedBy|generatedBy|changedBy|createdBy|updatedBy|decidedBy|acceptedBy|userId|actor)\\(\\s*\"");
    private static final Pattern SYSTEM_LITERAL = Pattern.compile("\"system\"");

    /** Files where {@code "system"} is not an actor, with the reason. */
    private static final Map<String, String> SYSTEM_LITERAL_ALLOWLIST = Map.of(
            "ai-service/src/main/java/com/opencrm/ai/selection/execution/OpenRouterExecutionClient.java",
            "the chat message role of a system prompt");

    /** Last key segments that hold secrets (lower case, without dashes and underscores). */
    private static final Set<String> SECRET_KEYS = Set.of("password", "secret", "clientsecret", "apikey", "accesskey",
            "accountkey", "privatekey", "connectionstring", "sastoken", "token", "credentials");
    /** Profiles that only ever run on a laptop or in CI, where a well-known default is allowed. */
    private static final Set<String> LOCAL_PROFILES = Set.of("dev", "test", "local");
    /** {@code ${NAME}} or {@code ${NAME:}}: resolved from the environment, no secret default. */
    private static final Pattern PLACEHOLDER_WITHOUT_DEFAULT = Pattern.compile("^\\$\\{[A-Za-z0-9_.\\-]+:?}$");

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
    @DisplayName("no request parameter or header names who acted; the actor comes from the token (D6)")
    void noIdentityFromRequestParameters() {
        List<String> violations = new ArrayList<>();
        for (ServiceSources service : services) {
            service.javaFiles().forEach((path, text) -> {
                Matcher matcher = REQUEST_INPUT.matcher(text);
                while (matcher.find()) {
                    List<String> names = new ArrayList<>(List.of(matcher.group(3)));
                    if (matcher.group(2) != null) {
                        Matcher quoted = QUOTED.matcher(matcher.group(2));
                        while (quoted.find()) {
                            names.add(quoted.group(1));
                        }
                    }
                    names.stream().filter(SecurityBaselineFitnessTest::isIdentityName)
                            .forEach(name -> violations.add(path + ": " + name));
                }
            });
        }
        assertThat(violations).as("record CurrentActor.id() (libs/security) instead of a request input").isEmpty();
    }

    @Test
    @DisplayName("request DTOs reject actors or omit identity fields (D6)")
    void noIdentityInRequestBodies() {
        List<String> violations = new ArrayList<>();
        Map<String, Set<String>> observedExceptions = new HashMap<>();
        int requestTypes = 0;
        for (ServiceSources service : services) {
            for (Map.Entry<String, String> file : service.javaFiles().entrySet()) {
                // Request types by naming convention; an @Entity named *Request (ApprovalRequest) is a record of the
                // request, where the service writes the actor, not something a caller sends.
                if (!file.getKey().endsWith("Request.java") || file.getValue().contains("@Entity")) {
                    continue;
                }
                requestTypes++;
                List<String> names = new ArrayList<>();
                Matcher field = FIELD.matcher(file.getValue());
                while (field.find()) {
                    names.add(field.group(1));
                }
                Matcher record = RECORD_COMPONENTS.matcher(file.getValue());
                while (record.find()) {
                    for (String component : record.group(1).split(",")) {
                        String[] words = component.trim().split("\\s+");
                        names.add(words[words.length - 1]);
                    }
                }
                names.stream().filter(SecurityBaselineFitnessTest::isIdentityName).forEach(name -> {
                    if (REQUEST_BODY_IDENTITY_EXCEPTIONS.getOrDefault(file.getKey(), Set.of()).contains(name)) {
                        observedExceptions.computeIfAbsent(file.getKey(), ignored -> new TreeSet<>()).add(name);
                    } else {
                        violations.add(file.getKey() + ": " + name);
                    }
                });
            }
        }
        assertThat(requestTypes).as("the scan finds request types").isGreaterThan(20);
        assertThat(violations).as("the service records the actor itself (CurrentActor)").isEmpty();
        assertThat(observedExceptions).as("the narrowly scoped field-registry exceptions remain current")
                .isEqualTo(REQUEST_BODY_IDENTITY_EXCEPTIONS);
    }

    @Test
    @DisplayName("no code records a hard-coded actor such as \"system\" (D6)")
    void noHardCodedActors() {
        List<String> violations = new ArrayList<>();
        Set<String> systemLiterals = new TreeSet<>();
        for (ServiceSources service : services) {
            service.javaFiles().forEach((path, text) -> {
                Matcher literal = LITERAL_ACTOR.matcher(text);
                while (literal.find()) {
                    violations.add(path + ": " + literal.group());
                }
                if (SYSTEM_LITERAL.matcher(text).find()) {
                    systemLiterals.add(path);
                }
            });
        }
        assertThat(violations).as("record CurrentActor.id(), or CurrentActor.self() for background work").isEmpty();
        assertThat(systemLiterals).as("\"system\" is not an actor; use CurrentActor.self()")
                .isEqualTo(new TreeSet<>(SYSTEM_LITERAL_ALLOWLIST.keySet()));
    }

    private static boolean isIdentityName(String name) {
        return IDENTITY_NAMES.contains(name.toLowerCase(java.util.Locale.ROOT).replace("-", "").replace("_", ""))
                || name.toLowerCase(java.util.Locale.ROOT).startsWith("x-user")
                || name.toLowerCase(java.util.Locale.ROOT).startsWith("x-actor");
    }

    @Test
    @DisplayName("no secret in committed configuration outside the local profiles (step 5 go-live gate)")
    @SuppressWarnings("unchecked")
    void noSecretsInConfiguration() throws IOException {
        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (ServiceSources service : services) {
            Path resources = servicesDir.resolve(service.name()).resolve("src/main/resources");
            if (!Files.isDirectory(resources)) {
                continue;
            }
            try (Stream<Path> files = Files.list(resources)) {
                for (Path file : files.filter(p -> p.getFileName().toString().matches("application.*\\.ya?ml")).toList()) {
                    String name = file.getFileName().toString();
                    Matcher profileFile = Pattern.compile("application-([a-z0-9]+)\\.ya?ml").matcher(name);
                    if (profileFile.matches() && LOCAL_PROFILES.contains(profileFile.group(1))) {
                        continue;
                    }
                    for (Object document : new Yaml().loadAll(Files.readString(file, StandardCharsets.UTF_8))) {
                        if (!(document instanceof Map<?, ?> map) || onlyLocalProfiles((Map<String, Object>) map)) {
                            continue;
                        }
                        checked++;
                        collectSecrets(service.name() + "/" + name, "", map, violations);
                    }
                }
            }
        }
        assertThat(checked).as("configuration documents checked").isGreaterThan(10);
        assertThat(violations)
                .as("use ${ENV_VAR} with no default; put local defaults in a dev | test profile document")
                .isEmpty();
    }

    /** Whether the document is active only in local profiles ({@code spring.config.activate.on-profile}). */
    private static boolean onlyLocalProfiles(Map<String, Object> document) {
        Object onProfile = nested(document, "spring", "config", "activate", "on-profile");
        if (onProfile == null || onProfile.toString().contains("!")) {
            return false;
        }
        return java.util.Arrays.stream(onProfile.toString().split("[|&,()\\s]+"))
                .filter(profile -> !profile.isBlank())
                .allMatch(LOCAL_PROFILES::contains);
    }

    private static void collectSecrets(String file, String prefix, Map<?, ?> map, List<String> violations) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (entry.getValue() instanceof Map<?, ?> child) {
                collectSecrets(file, path, child, violations);
                continue;
            }
            String last = path.substring(path.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT)
                    .replace("-", "").replace("_", "");
            if (!SECRET_KEYS.contains(last) || entry.getValue() == null) {
                continue;
            }
            String value = String.valueOf(entry.getValue()).trim();
            if (!value.isEmpty() && !PLACEHOLDER_WITHOUT_DEFAULT.matcher(value).matches()) {
                violations.add(file + ": " + path + "=" + value);
            }
        }
    }

    @Test
    @DisplayName("the Entra manifest agrees with the code: roles, token version, idtyp, redirect, workloads (step 5)")
    void entraManifestMatchesTheCode() throws IOException {
        Path manifestFile = servicesDir.getParent().getParent().resolve("infra/entra/opencrm-identity.json");
        assertThat(manifestFile).as("the Entra manifest").isRegularFile();
        String manifestText = Files.readString(manifestFile, StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.JsonNode manifest = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(manifestText);
        com.fasterxml.jackson.databind.JsonNode api = manifest.path("api");

        Map<String, String> declaredRoles = new java.util.TreeMap<>();
        api.path("appRoles").forEach(role -> declaredRoles.put(role.path("value").asText(),
                role.path("allowedMemberTypes").toString()));
        Set<String> rolesInCode = new TreeSet<>(Set.of(new OpenCrmSecurityProperties().getInternalRole(), "CPQ.User",
                "Admin"));
        Pattern roleReference = Pattern.compile("authorityFor\\(\\s*\"([^\"]+)\"\\s*\\)");
        for (ServiceSources service : services) {
            service.javaFiles().values().forEach(text -> {
                Matcher matcher = roleReference.matcher(text);
                while (matcher.find()) {
                    rolesInCode.add(matcher.group(1));
                }
            });
        }
        assertThat(rolesInCode).as("roles the code checks").contains("DealDesk", "SalesVP", "CFO");
        assertThat(declaredRoles.keySet()).as("every role the code checks is an app role").containsAll(rolesInCode);
        assertThat(declaredRoles.get("Internal.Invoke")).as("Internal.Invoke is for applications only")
                .isEqualTo("[\"Application\"]");
        rolesInCode.stream().filter(role -> !role.equals("Internal.Invoke")).forEach(role ->
                assertThat(declaredRoles.get(role)).as(role + " is for users only").isEqualTo("[\"User\"]"));

        assertThat(api.path("requestedAccessTokenVersion").asInt())
                .as("v2.0 tokens: the issuer is the v2.0 endpoint and aud is the client id").isEqualTo(2);
        assertThat(api.path("optionalClaims").path("accessToken").toString())
                .as("idtyp marks service tokens explicitly").contains("\"idtyp\"");
        assertThat(api.path("delegatedScopes").size()).as("user tokens need a delegated scope (scp)").isPositive();

        ServiceSources gateway = services.stream().filter(s -> s.name().equals("gateway-service")).findFirst()
                .orElseThrow();
        Matcher registrationId = Pattern.compile("registrationId\\s*=\\s*\"([^\"]+)\"").matcher(gateway.javaFiles()
                .get("gateway-service/src/main/java/com/opencrm/gateway/security/GatewayBffProperties.java"));
        assertThat(registrationId.find()).isTrue();
        assertThat(manifest.path("gateway").path("redirectPath").asText())
                .isEqualTo("/login/oauth2/code/" + registrationId.group(1));
        manifest.path("gateway").path("redirectUris").forEach(uri -> assertThat(uri.asText())
                .as("redirect URIs are https").startsWith("https://")
                .endsWith("/login/oauth2/code/" + registrationId.group(1)));

        Map<String, String> workloads = new java.util.TreeMap<>();
        manifest.path("workloads").forEach(workload -> workloads.put(workload.path("service").asText(),
                workload.path("appRoles").toString()));
        for (ServiceSources service : services) {
            boolean callsInternal = service.javaFiles().values().stream().anyMatch(t -> t.contains("serviceToken()"));
            boolean callsServices = callsInternal
                    || service.javaFiles().values().stream().anyMatch(t -> t.contains("relayUserToken()"));
            if (callsServices) {
                assertThat(workloads).as(service.name() + " calls other services, so it needs a workload identity")
                        .containsKey(service.name());
            }
            if (callsInternal) {
                assertThat(workloads.get(service.name())).as(service.name() + " calls /internal/**")
                        .contains("Internal.Invoke");
            }
        }
        assertThat(manifestText.toLowerCase(java.util.Locale.ROOT))
                .as("the manifest holds no credential").doesNotContain("clientsecret").doesNotContain("\"password\"");
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

    @Test
    @DisplayName("every service ships the Prometheus registry and exposes only health, info and prometheus (owner decision 4)")
    void metricsAreScrapedAndNothingElseIsExposed() {
        List<String> withoutRegistry = new ArrayList<>();
        Map<String, Set<String>> exposedBeyondTheList = new java.util.TreeMap<>();
        List<String> notScraped = new ArrayList<>();
        for (ServiceSources service : services) {
            if (!service.pom().contains("<artifactId>micrometer-registry-prometheus</artifactId>")) {
                withoutRegistry.add(service.name());
            }
            Set<String> exposed = new TreeSet<>();
            for (Map<String, Object> document : service.configDocuments()) {
                Object include = nested(document, "management", "endpoints", "web", "exposure", "include");
                if (include != null) {
                    for (String id : include.toString().split(",")) {
                        exposed.add(id.trim().toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
            if (!exposed.contains("prometheus")) {
                notScraped.add(service.name());
            }
            exposed.removeAll(Set.of("health", "info", "prometheus"));
            if (service.name().equals("gateway-service")) {
                exposed.remove("gateway"); // read-only route inspection, refused on the management port
            }
            if (!exposed.isEmpty()) {
                exposedBeyondTheList.put(service.name(), exposed);
            }
        }
        assertThat(withoutRegistry).as("add micrometer-registry-prometheus (runtime): the scrape endpoint needs it").isEmpty();
        assertThat(notScraped).as("management.endpoints.web.exposure.include must list prometheus").isEmpty();
        assertThat(exposedBeyondTheList)
                .as("metrics, env, beans and the rest are not exposed by default; ADR-018 alerts read the scrape")
                .isEmpty();
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
