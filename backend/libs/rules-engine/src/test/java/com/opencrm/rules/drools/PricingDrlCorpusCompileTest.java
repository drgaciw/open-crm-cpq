package com.opencrm.rules.drools;

import com.opencrm.rules.engine.RuleDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1-5: verbatim compile of the pricing-service DRL corpus on the parent Drools BOM (10.2.0).
 *
 * <p>Phase 0 catalog compiled here is 7 files. DRG-542 removed {@code amendment-proration.drl};
 * {@code docs/phase0/phase0-drools-compatibility.md} and
 * {@code docs/phase0/phase0-rule-catalog-reconciliation.md} still name the historical eight.
 * T1-3 added 3 metric-gated policy DRLs. This suite compiles every {@code .drl} in the
 * service rules directory.
 */
@Tag("unit")
@DisplayName("Pricing DRL corpus compile (Drools 10.2.0)")
class PricingDrlCorpusCompileTest {

    static final List<String> PHASE0_DRLS = List.of(
            "volume-discount-named-seat.drl",
            "usage-overage-charge.drl",
            "perpetual-maintenance.drl",
            "core-license-pack.drl",
            "migration-credit.drl",
            "approval-margin-exception.drl",
            "renewal-uplift.drl"
    );

    static final List<String> T13_POLICY_DRLS = List.of(
            "tcv-pricing-tiers.drl",
            "acv-minimum-commitment.drl",
            "churn-risk-adjustment.drl"
    );

    private final DroolsRuleCompiler compiler = new DroolsRuleCompiler();

    @Test
    void resolvedDroolsRuntimeIs1020() throws IOException {
        String version = readDroolsArtifactVersion();
        assertThat(version)
                .as("org.drools artifact on the test classpath")
                .isEqualTo("10.2.0");
    }

    @Test
    void phase0SevenDrlsArePresent() throws IOException {
        List<String> names = corpusFileNames();
        assertThat(PHASE0_DRLS).hasSize(7);
        assertThat(names).containsAll(PHASE0_DRLS);
        assertThat(names).doesNotContain("amendment-proration.drl");
    }

    @Test
    void fullCorpusIncludesPhase0AndT13PolicyDrls() throws IOException {
        List<String> names = corpusFileNames();
        assertThat(names).containsAll(PHASE0_DRLS);
        assertThat(names).containsAll(T13_POLICY_DRLS);
        assertThat(names).hasSizeGreaterThanOrEqualTo(PHASE0_DRLS.size());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpusDrlFiles")
    void eachCorpusDrlCompiles(Path drlFile) throws IOException {
        String content = Files.readString(drlFile);
        DroolsCompilationResult result = compiler.validateDrl(content);

        assertThat(result.isValid())
                .as("%s errors: %s", drlFile.getFileName(), result.joinedErrors())
                .isTrue();
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getKieBase()).isNotNull();
        assertThat(result.getKieBase().newKieSession()).isNotNull();
    }

    @Test
    void fullCorpusCompilesTogether() throws IOException {
        List<Path> files = corpusDrlFiles().toList();
        assertThat(files).isNotEmpty();

        List<RuleDefinition> rules = new ArrayList<>();
        for (Path file : files) {
            rules.add(RuleDefinition.builder()
                    .id(file.getFileName().toString().replace(".drl", ""))
                    .name(file.getFileName().toString())
                    .content(Files.readString(file))
                    .build());
        }

        DroolsCompilationResult result = compiler.validateRules(rules);
        assertThat(result.isValid())
                .as("combined corpus errors: %s", result.joinedErrors())
                .isTrue();

        KieBase kieBase = compiler.compileRules(rules);
        assertThat(kieBase).isNotNull();
        assertThat(kieBase.newKieSession()).isNotNull();
    }

    static Stream<Path> corpusDrlFiles() throws IOException {
        return listCorpusDrls().stream();
    }

    private static List<String> corpusFileNames() throws IOException {
        return listCorpusDrls().stream()
                .map(path -> path.getFileName().toString())
                .toList();
    }

    private static List<Path> listCorpusDrls() throws IOException {
        Path rulesDir = resolvePricingRulesDir();
        try (Stream<Path> stream = Files.list(rulesDir)) {
            List<Path> files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".drl"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            assertThat(files)
                    .as("DRL files under %s", rulesDir)
                    .isNotEmpty();
            return files;
        }
    }

    static Path resolvePricingRulesDir() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> roots = new ArrayList<>();
        roots.add(cwd);
        String basedir = System.getProperty("basedir");
        if (basedir != null && !basedir.isBlank()) {
            roots.add(Path.of(basedir).toAbsolutePath().normalize());
        }

        for (Path root : roots) {
            Path current = root;
            for (int i = 0; i < 8 && current != null; i++) {
                Path fromRepo = current.resolve("backend/services/pricing-service/src/main/resources/rules");
                if (Files.isDirectory(fromRepo)) {
                    return fromRepo;
                }
                Path fromBackend = current.resolve("services/pricing-service/src/main/resources/rules");
                if (Files.isDirectory(fromBackend)) {
                    return fromBackend;
                }
                current = current.getParent();
            }
        }

        throw new AssertionError("Could not locate pricing DRL corpus from user.dir="
                + cwd + " basedir=" + basedir);
    }

    private static String readDroolsArtifactVersion() throws IOException {
        String[] artifacts = {"drools-engine", "drools-core", "drools-compiler", "drools-mvel"};
        for (String artifact : artifacts) {
            String resource = "META-INF/maven/org.drools/" + artifact + "/pom.properties";
            try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                Properties properties = new Properties();
                properties.load(in);
                String version = properties.getProperty("version");
                if (version != null && !version.isBlank()) {
                    return version;
                }
            }
        }
        throw new AssertionError("No org.drools/*/pom.properties found on the test classpath");
    }
}
