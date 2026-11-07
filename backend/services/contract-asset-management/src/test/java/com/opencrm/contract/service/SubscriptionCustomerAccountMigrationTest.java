package com.opencrm.contract.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** DRG-663 AC1: CAM V1_20 adds nullable subscriptions.customer_account_id with rollback. */
@Tag("unit")
class SubscriptionCustomerAccountMigrationTest {

    @Test
    void v120AddsNullableUuidCustomerAccountWithRollback() throws IOException {
        String migration = resource("db/migration/V1_20__subscriptions_customer_account_id.sql");
        String executable = migration.replaceAll("(?m)--.*$", "");

        assertThat(executable).contains("ALTER TABLE subscriptions");
        assertThat(executable).contains("ADD COLUMN customer_account_id uuid");
        assertThat(executable.toLowerCase()).doesNotContain("not null");
        assertThat(executable.toLowerCase()).doesNotContain("backfill");
        assertThat(migration).containsIgnoringCase("rollback");
        assertThat(migration).doesNotContain("V1_16__");
        assertThat(migration).doesNotContain("V1_17__");
        assertThat(migration).doesNotContain("V1_18__");
        assertThat(migration).doesNotContain("V1_19__");

        String rollback = resource("db/rollback/V1_20__drop_subscriptions_customer_account_id.sql");
        assertThat(rollback).contains("DROP COLUMN");
        assertThat(rollback).contains("customer_account_id");
        assertThat(rollback).contains("1.20");
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = SubscriptionCustomerAccountMigrationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
