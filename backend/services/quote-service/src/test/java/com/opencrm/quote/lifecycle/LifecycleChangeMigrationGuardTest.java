package com.opencrm.quote.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Docker-free migration count. The count is this list, not a floor.
 * V1_13 is quote tenant_id; V1_14 creates lifecycle tables and V1_15 adds validation evidence.
 * Rejections go to {@code lifecycle_change_audit} in that same file, not a second migration,
 * and not {@code commercial_decision_audit} (CPQ Decision 2026-10-26, option b).
 */
@DisplayName("DRG-583 lifecycle migration guard")
class LifecycleChangeMigrationGuardTest {

    /** Quote-service Flyway versions on main; the list is the guard, not a floor. */
    private static final List<String> VERSIONS = List.of(
            "V1__create_quote_schema.sql",
            "V1_4__create_outbox_and_update_quotes.sql",
            "V1_5__create_jev_decision_log.sql",
            "V1_6__outbox_relay_status_and_leasing.sql",
            "V1_7__outbox_event_envelope_columns.sql",
            "V1_8__outbox_status_skipped.sql",
            "V1_9__quote_ramp_steps.sql",
            "V1_10__quote_subscription_continuation.sql",
            "V1_11__processed_events_inbox.sql",
            "V1_12__continuation_operation.sql",
            "V1_13__quote_tenant_id.sql",
            "V1_14__lifecycle_changes.sql",
            "V1_15__lifecycle_pricing_validation.sql",
            "V1_16__ramp_escalator_compounding.sql",
            "V1_17__accepted_order_snapshots.sql",
            "V1_18__outbox_delivery_leases.sql",
            "V1_19__quote_revision_requests.sql");

    @Test
    @DisplayName("quote-service has exactly these versions, including lifecycle validation evidence")
    void versionCountIsTheList() throws IOException {
        try (var files = Files.list(migrationDir())) {
            List<String> names = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .sorted()
                    .toList();
            assertThat(names).containsExactlyInAnyOrderElementsOf(VERSIONS);
        }
    }

    @Test
    @DisplayName("V1_14 creates lifecycle_change_audit in the same file and does not touch the quote audit")
    void auditTableIsInTheSameExpandOnlyMigration() throws IOException {
        String sql = Files.readString(migrationDir().resolve("V1_14__lifecycle_changes.sql"));
        assertThat(sql).contains("CREATE TABLE lifecycle_changes");
        assertThat(sql).contains("CREATE TABLE lifecycle_change_lines");
        assertThat(sql).contains("CREATE TABLE lifecycle_change_audit");
        assertThat(sql).contains("tid");
        assertThat(sql).doesNotContain("commercial_decision_audit");
        assertThat(sql).doesNotContain("REFERENCES quotes");
        assertThat(sql).doesNotContain("V1_11");

        int auditDrop = sql.indexOf("DROP TABLE IF EXISTS lifecycle_change_audit");
        int lineDrop = sql.indexOf("DROP TABLE IF EXISTS lifecycle_change_lines");
        int changeDrop = sql.indexOf("DROP TABLE IF EXISTS lifecycle_changes;");
        assertThat(auditDrop).isGreaterThanOrEqualTo(0);
        assertThat(auditDrop).isLessThan(lineDrop);
        assertThat(lineDrop).isLessThan(changeDrop);

        String audit = sql.substring(sql.indexOf("CREATE TABLE lifecycle_change_audit"));
        assertThat(audit).contains("tid VARCHAR(64) NOT NULL");
        assertThat(audit).contains("lifecycle_change_id UUID");
        assertThat(audit).doesNotContain("lifecycle_change_id UUID NOT NULL");
        assertThat(audit).contains("decision_kind");
        assertThat(audit).contains("reason_code");
        assertThat(audit).contains("decided_by");
        assertThat(audit).contains("inputs_json");
        assertThat(audit).contains("outputs_json");
        assertThat(audit).contains("created_at");
        assertThat(audit).doesNotContain("quote_id");
        assertThat(audit).doesNotContain("REFERENCES quotes");
        assertThat(audit).doesNotContain("REFERENCES lifecycle_changes");
    }

    private static Path migrationDir() {
        Path module = Path.of("src/main/resources/db/migration");
        if (Files.isDirectory(module)) {
            return module;
        }
        Path fromRoot = Path.of("backend/services/quote-service/src/main/resources/db/migration");
        if (Files.isDirectory(fromRoot)) {
            return fromRoot;
        }
        throw new IllegalStateException("migration directory not found from " + Path.of("").toAbsolutePath());
    }
}
