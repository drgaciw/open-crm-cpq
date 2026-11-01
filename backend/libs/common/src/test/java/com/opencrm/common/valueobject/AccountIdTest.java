package com.opencrm.common.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountIdTest {
    private static final String UUID = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    void wrapsThePublicUuid() {
        assertEquals(UUID, AccountId.of(UUID).value());
        assertEquals(AccountId.of(UUID), AccountId.of(UUID));
        assertNotEquals(AccountId.of(UUID), AccountId.of("550e8400-e29b-41d4-a716-446655440001"));
    }

    @Test
    void rejectsNullAndNonCanonicalUuid() {
        assertThrows(IllegalArgumentException.class, () -> AccountId.of(null));
        assertThrows(IllegalArgumentException.class, () -> AccountId.of("42"));
        assertThrows(IllegalArgumentException.class, () -> AccountId.of("not-a-uuid"));
    }
}
