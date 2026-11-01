package com.opencrm.common.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AccountId is the shared-kernel identifier for an organization (the account of BC-12-01).
 * It wraps the existing organization {@code Long} id. The wire format stays {@code Long}.
 */
@DisplayName("AccountId")
class AccountIdTest {

    @Nested
    @DisplayName("of")
    class Of {

        @Test
        @DisplayName("wraps a positive organization id")
        void wrapsPositiveId() {
            AccountId accountId = AccountId.of(42L);

            assertEquals(42L, accountId.value());
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertThrows(IllegalArgumentException.class, () -> AccountId.of(null));
        }

        @Test
        @DisplayName("rejects zero")
        void rejectsZero() {
            assertThrows(IllegalArgumentException.class, () -> AccountId.of(0L));
        }

        @Test
        @DisplayName("rejects a negative id")
        void rejectsNegative() {
            assertThrows(IllegalArgumentException.class, () -> AccountId.of(-1L));
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("equal ids are equal")
        void equalIds() {
            assertEquals(AccountId.of(7L), AccountId.of(7L));
            assertEquals(AccountId.of(7L).hashCode(), AccountId.of(7L).hashCode());
        }

        @Test
        @DisplayName("different ids are not equal")
        void differentIds() {
            assertNotEquals(AccountId.of(7L), AccountId.of(8L));
        }
    }
}
