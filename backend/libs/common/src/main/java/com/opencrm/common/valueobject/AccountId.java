package com.opencrm.common.valueobject;

/**
 * Shared-kernel identifier for an account. An account is the CRM organization, and this wraps that
 * row's existing {@code Long} id. It is not a JPA embeddable and it is not a Spring bean: JSON
 * fields stay {@code Long}.
 */
public record AccountId(Long value) {

    public AccountId {
        if (value == null) {
            throw new IllegalArgumentException("Account id cannot be null");
        }
        if (value <= 0) {
            throw new IllegalArgumentException("Account id must be positive");
        }
    }

    public static AccountId of(Long value) {
        return new AccountId(value);
    }
}
