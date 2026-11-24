package com.opencrm.contract.asset;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every GET under {@code /api/v1/assets/**} must enter the caller tenant scope.
 * A new mapping that skips InstalledBaseReadScope fails this test.
 */
class AssetReadEndpointCoverageTest {

    private static final List<String> SCOPED_CALLS = List.of(
            "assets.list(",
            "assets.get(",
            "history.history(",
            "history.chain(",
            "history.installedBase(",
            "reports.upcomingRenewals(",
            "reports.churn(");

    @Test
    void everyAssetGetEntersTheCallerScope() throws Exception {
        assertThat(source("AssetQueryService.java")).doesNotContain("findByDeletedFalse()");
        for (String method : List.of("list", "get")) {
            assertThat(methodBody(source("AssetQueryService.java"), method))
                    .contains("InstalledBaseReadScope.current()");
        }
        for (String method : List.of("history", "chain", "installedBase")) {
            assertThat(methodBody(source("AssetHistoryProjection.java"), method))
                    .contains("InstalledBaseReadScope.current()");
        }
        for (String method : List.of("upcomingRenewals", "churn")) {
            assertThat(methodBody(source("AssetReportService.java"), method))
                    .contains("InstalledBaseReadScope.current()");
        }
        for (String controller : List.of(
                "AssetController.java", "AssetQueryController.java", "AssetReportController.java")) {
            String mappings = source(controller);
            int first = mappings.indexOf("@GetMapping");
            assertThat(first).as(controller).isGreaterThanOrEqualTo(0);
            for (String chunk : mappings.substring(first).split("(?=@GetMapping)")) {
                if (chunk.isBlank()) {
                    continue;
                }
                assertThat(SCOPED_CALLS.stream().anyMatch(chunk::contains))
                        .as(controller + " mapping delegates to a scoped read:%n%s", chunk)
                        .isTrue();
            }
        }
    }

    private static String methodBody(String source, String method) {
        String signature = method + "(";
        int start = source.indexOf(signature);
        assertThat(start).as(method).isGreaterThanOrEqualTo(0);
        int next = source.indexOf("\n    public ", start + signature.length());
        if (next < 0) {
            next = source.indexOf("\n    private ", start + signature.length());
        }
        return next < 0 ? source.substring(start) : source.substring(start, next);
    }

    private static String source(String fileName) throws Exception {
        Path path = Path.of("src/main/java/com/opencrm/contract/asset").resolve(fileName);
        assertThat(path).exists();
        return Files.readString(path);
    }
}
