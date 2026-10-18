package com.opencrm.contract.documents.apply;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-S4-9. The new orchestrator does not call the legacy acceptance path.
 * Flag-off behaviour cannot be verified until #683 merges; this scan is the regression that is possible on main.
 */
@Tag("unit")
class DocumentApplyLegacyIsolationTest {

    @Test
    void orchestratorSourcesDoNotReferenceTheLegacyApplyPathOrKafkaApplyDtos() throws IOException {
        Path root = repositoryFile("backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/apply");
        StringBuilder sources = new StringBuilder();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                sources.append(Files.readString(file)).append('\n');
            }
        }
        assertThat(sources).isNotBlank();
        assertThat(sources).doesNotContain("AppliedQuoteAcceptanceRepository");
        assertThat(sources).doesNotContain("AcceptedQuoteSubscriptionService");
        assertThat(sources).doesNotContain("SubscriptionRateApplyService");
        assertThat(sources).doesNotContain("KafkaTemplate");
        assertThat(sources).doesNotContain("AmendmentApplied");
        assertThat(sources).doesNotContain("AmendmentApplyBlocked");
        assertThat(sources).doesNotContain("AmendmentUnitApplied");
        assertThat(sources).doesNotContain("@PreAuthorize");
        assertThat(sources).doesNotContain("hasRole(");
    }

    private static Path repositoryFile(String relative) {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null && !Files.exists(cursor.resolve(relative))) {
            cursor = cursor.getParent();
        }
        assertThat(cursor).as(relative).isNotNull();
        return cursor.resolve(relative);
    }
}
