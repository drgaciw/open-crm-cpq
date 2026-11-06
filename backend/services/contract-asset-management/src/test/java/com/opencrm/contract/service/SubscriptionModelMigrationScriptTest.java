package com.opencrm.contract.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-666 AC1 — Docker-free guard for the additive subscription_model migration.
 * CAM slot V1_19 — leaves V1_16..V1_18 for sibling drafts DRG-662 / DRG-665.
 */
@Tag("unit")
class SubscriptionModelMigrationScriptTest {

    @Test
    void v118AddsNullableSubscriptionModelWithAdrEnumCheck() throws IOException {
        String sql = script("db/migration/V1_19__subscription_model.sql");
        String body = sql.replaceAll("(?m)--.*$", "");

        assertThat(body).contains("ADD COLUMN subscription_model");
        assertThat(body).contains("SAAS_RECURRING");
        assertThat(body).contains("ONPREM_TRANSACTIONAL");
        assertThat(body).contains("ONPREM_CAPACITY");
        assertThat(body).contains("subscription_ingest_audits");
        assertThat(body).contains("SUBSCRIPTION_INGEST_REJECTED");
        assertThat(body).doesNotContain("SubscriptionActivated");
        assertThat(body).doesNotContain("EntitlementProvisioned");
        assertThat(sql.toLowerCase()).contains("rollback");
    }

    private static String script(String path) throws IOException {
        try (var in = SubscriptionModelMigrationScriptTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("expected resource %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
