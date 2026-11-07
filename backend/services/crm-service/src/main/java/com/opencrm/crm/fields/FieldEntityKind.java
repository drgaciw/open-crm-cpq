package com.opencrm.crm.fields;

import java.util.Locale;

/**
 * CRM record the registry can attach to. Lead is out of this slice.
 */
public enum FieldEntityKind {
    ORGANIZATION,
    CONTACT,
    DEAL;

    public String stored() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FieldEntityKind fromStored(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Field entity is required");
        }
        for (FieldEntityKind kind : values()) {
            if (kind.stored().equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown field entity: " + value);
    }
}
