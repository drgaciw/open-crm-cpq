package com.opencrm.contract.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-664 AC2 — Docker-free guard for the additive {@code renewal_mode} migration.
 * CAM slot {@code V1_21} — Wave 2 reserved V1_16..V1_20 for sibling drafts (#805–#808, DRG-663).
 */
@Tag("unit")
class SubscriptionRenewalModeMigrationScriptTest {

    @Test
    void v121AddsNullableRenewalModeWithAutoRollManualCheck() throws IOException {
        String sql = script("db/migration/V1_21__subscription_renewal_mode.sql");
        String body = sql.replaceAll("(?m)--.*$", "");

        assertThat(body).contains("ADD COLUMN renewal_mode");
        assertThat(body).contains("AUTO_ROLL");
        assertThat(body).contains("MANUAL");
        assertThat(body).contains("chk_subscription_renewal_mode");
        // AC3: PERPETUAL null end_date CHECK change is NO-GO in this PR.
        assertThat(body).doesNotContain("chk_contracts_dates");
        assertThat(body).doesNotContain("PERPETUAL");
        assertThat(sql.toLowerCase()).contains("rollback");
    }

    private static String script(String path) throws IOException {
        try (var in = SubscriptionRenewalModeMigrationScriptTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("expected resource %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
