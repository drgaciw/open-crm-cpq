package com.opencrm.common.valueobject;

import java.util.UUID;

/**
 * Shared-kernel account identity: the CRM organization's public UUID, not its local numeric row ID.
 */
public record AccountId(String value) {

    public AccountId {
        if (value == null) {
            throw new IllegalArgumentException("Account id cannot be null");
        }
        String canonical;
        try {
            canonical = UUID.fromString(value).toString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Account id must be a UUID", ex);
        }
        if (!canonical.equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Account id must be a canonical UUID");
        }
        value = canonical;
    }

    public static AccountId of(String value) {
        return new AccountId(value);
    }
}
