package com.opencrm.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuditHashingTest {

    private static final Instant WHEN = Instant.parse("2026-11-11T10:00:00Z");

    @Test
    void genesisHashIs64ZeroCharacters() {
        assertThat(AuditHashing.GENESIS_HASH).hasSize(64).matches("0{64}");
    }

    @Test
    void hashIsDeterministicForTheSameInputs() {
        String first = AuditHashing.hash(AuditHashing.GENESIS_HASH, "tenant-1", "Lead", "42", "user:abc",
                "CREATE", "{}", WHEN);
        String second = AuditHashing.hash(AuditHashing.GENESIS_HASH, "tenant-1", "Lead", "42", "user:abc",
                "CREATE", "{}", WHEN);

        assertThat(first).isEqualTo(second).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void hashChangesWhenPreviousHashChanges() {
        String withGenesis = AuditHashing.hash(AuditHashing.GENESIS_HASH, "t", "Lead", "1", "p", "CREATE", "d", WHEN);
        String withOtherPrevious = AuditHashing.hash("a".repeat(64), "t", "Lead", "1", "p", "CREATE", "d", WHEN);

        assertThat(withGenesis).isNotEqualTo(withOtherPrevious);
    }

    @Test
    void hashChangesWhenAnyContentFieldChanges() {
        String base = AuditHashing.hash(AuditHashing.GENESIS_HASH, "tenant", "Lead", "42", "user:abc", "CREATE",
                "details", WHEN);

        assertThat(AuditHashing.hash(AuditHashing.GENESIS_HASH, "OTHER", "Lead", "42", "user:abc", "CREATE",
                "details", WHEN)).isNotEqualTo(base);
        assertThat(AuditHashing.hash(AuditHashing.GENESIS_HASH, "tenant", "Deal", "42", "user:abc", "CREATE",
                "details", WHEN)).isNotEqualTo(base);
        assertThat(AuditHashing.hash(AuditHashing.GENESIS_HASH, "tenant", "Lead", "OTHER", "user:abc", "CREATE",
                "details", WHEN)).isNotEqualTo(base);
        assertThat(AuditHashing.hash(AuditHashing.GENESIS_HASH, "tenant", "Lead", "42", "user:OTHER", "CREATE",
                "details", WHEN)).isNotEqualTo(base);
        assertThat(AuditHashing.hash(AuditHashing.GENESIS_HASH, "tenant", "Lead", "42", "user:abc", "UPDATE",
                "details", WHEN)).isNotEqualTo(base);
        assertThat(AuditHashing.hash(AuditHashing.GENESIS_HASH, "tenant", "Lead", "42", "user:abc", "CREATE",
                "OTHER", WHEN)).isNotEqualTo(base);
        assertThat(AuditHashing.hash(AuditHashing.GENESIS_HASH, "tenant", "Lead", "42", "user:abc", "CREATE",
                "details", WHEN.plusSeconds(1))).isNotEqualTo(base);
    }

    @Test
    void nullTenantPrincipalAndDetailsDoNotCollideWithTheLiteralTextOfThoseFields() {
        // Null fields are joined as empty strings; this is the regression the \u0001 field separator exists for -
        // without it, (tenant="AB", principal=null) and (tenant=null, principal="AB") would hash identically.
        String nullTenant = AuditHashing.hash(AuditHashing.GENESIS_HASH, null, "Lead", "1", "AB", "CREATE", null, WHEN);
        String emptyStringTenant = AuditHashing.hash(AuditHashing.GENESIS_HASH, "", "Lead", "1", "AB", "CREATE", null, WHEN);

        assertThat(nullTenant).isEqualTo(emptyStringTenant);
    }
}
