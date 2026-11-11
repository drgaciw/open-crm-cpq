package com.opencrm.crm.activity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The activities table is static DDL. Parent links and activity types are constrained
 * in SQL so a later mailbox writer cannot insert an unanchored row.
 */
class ActivityMigrationTest {

    @Test
    void timelineTableRequiresAParentAndTheFourActivityTypes() throws IOException {
        String sql;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V18__activities.sql")) {
            assertThat(in).as("V18 activities migration").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("CREATE TABLE activities");
        assertThat(sql).contains("CONSTRAINT ck_activities_parent");
        assertThat(sql).contains("organization_id IS NOT NULL");
        assertThat(sql).contains("contact_id IS NOT NULL");
        assertThat(sql).contains("deal_id IS NOT NULL");
        assertThat(sql).contains("'NOTE'");
        assertThat(sql).contains("'TASK'");
        assertThat(sql).contains("'EMAIL'");
        assertThat(sql).contains("'MEETING'");
        assertThat(sql).contains("email_thread_ref");
        assertThat(sql).contains("calendar_event_ref");
        assertThat(sql).doesNotContain("${");
    }
}
