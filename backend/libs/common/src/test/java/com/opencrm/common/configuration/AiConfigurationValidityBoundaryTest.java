package com.opencrm.common.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-module boundary: AI assistants must not assert configuration validity.
 */
@DisplayName("AI Configuration Validity Boundary")
class AiConfigurationValidityBoundaryTest {

    private static final Path AI_SERVICE_ROOT =
            Path.of("..", "..", "services", "ai-service", "src", "main", "java", "com", "opencrm", "ai");
    private static final Path AI_SERVICE_POM =
            Path.of("..", "..", "services", "ai-service", "pom.xml");

    @Test
    @DisplayName("ai-service must not depend on configuration-engine module")
    void aiServiceDoesNotDependOnConfigurationEngine() throws IOException {
        String pomContent = Files.readString(AI_SERVICE_POM);
        assertFalse(
                pomContent.contains("<artifactId>configuration-engine</artifactId>"),
                "ai-service must not declare a configuration-engine dependency"
        );
    }

    @Test
    @DisplayName("ai agents must not call configuration validity mutation APIs")
    void aiAgentsDoNotMutateConfigurationValidity() throws IOException {
        if (!Files.isDirectory(AI_SERVICE_ROOT)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(AI_SERVICE_ROOT)) {
            boolean violatesBoundary = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(AiConfigurationValidityBoundaryTest::readQuietly)
                    .anyMatch(content ->
                            content.contains("markAsValid")
                                    || content.contains("ConfigurationSessionService")
                                    || content.contains("configuration-engine")
                    );
            assertFalse(violatesBoundary, "AI code must not mutate configuration validity state");
        }
    }

    @Test
    @DisplayName("configuration validity owner is configuration-engine")
    void configurationValidityOwnerIsConfigurationEngine() {
        assertTrue(ConfigurationValidityAuthority.OWNER_SERVICE.equals("configuration-engine"));
    }

    private static String readQuietly(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return "";
        }
    }
}
