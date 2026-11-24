package com.opencrm.contract.asset;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Docker-free guard for the V1_13 and V1_14 rollbacks. Both scripts live under
 * db/rollback, which is not a Flyway migration location.
 */
@Tag("unit")
class AssetRollbackScriptTest {

    @Test
    void assetHistoryRollbackRunsBeforeTheAssetsRollback() throws IOException {
        String history = resource("db/rollback/V1_14__drop_asset_history.sql");
        String assets = resource("db/rollback/V1_13__drop_assets.sql");
        String historyBody = history.replaceAll("(?m)--.*$", "");
        String assetsBody = assets.replaceAll("(?m)--.*$", "");

        assertThat(history).startsWith("-- DATA-DESTRUCTIVE:");
        assertThat(assets).startsWith("-- DATA-DESTRUCTIVE:");
        assertThat(history).contains("Run this before V1_13__drop_assets.sql");
        assertThat(assets).contains("Run V1_14__drop_asset_history.sql before this script");

        int historyIndexes = historyBody.indexOf("DROP INDEX IF EXISTS idx_asset_history_asset");
        int historyTable = historyBody.indexOf("DROP TABLE IF EXISTS asset_history");
        assertThat(historyIndexes).isNotNegative().isLessThan(historyTable);
        assertThat(historyBody.indexOf("DROP CONSTRAINT IF EXISTS chk_asset_history_type")).isLessThan(historyTable);
        assertThat(historyBody).doesNotContain("DROP TABLE assets");
        assertThat(historyBody).doesNotContain("flyway_schema_history");

        int assetIndexes = assetsBody.indexOf("DROP INDEX IF EXISTS idx_asset_account");
        int assetTable = assetsBody.indexOf("DROP TABLE IF EXISTS assets");
        assertThat(assetIndexes).isNotNegative().isLessThan(assetTable);
        assertThat(assetsBody.indexOf("DROP CONSTRAINT IF EXISTS assets_subscription_id_fkey")).isLessThan(assetTable);
        assertThat(assetsBody).doesNotContain("asset_history");
        assertThat(assetsBody).doesNotContain("flyway_schema_history");
        assertThat(assetsBody).doesNotContain("subscriptions");
    }

    @Test
    void flywayConfigDoesNotScanTheRollbackDirectory() throws IOException {
        String application = resource("application.yml");
        assertThat(application).doesNotContain("db/rollback");
        assertThat(application).doesNotContain("classpath:db/rollback");
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = AssetRollbackScriptTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
