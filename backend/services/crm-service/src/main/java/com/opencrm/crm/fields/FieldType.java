package com.opencrm.crm.fields;

import java.util.Locale;

/**
 * Admin field types. Each maps to exactly one {@link StorageClass}.
 * Multi-select is out of scope.
 */
public enum FieldType {
    TEXT(StorageClass.TEXT),
    LONG_TEXT(StorageClass.TEXT),
    NUMBER(StorageClass.NUMERIC),
    DATE(StorageClass.DATE),
    CHECKBOX(StorageClass.BOOL),
    SELECT(StorageClass.OPTION),
    URL(StorageClass.TEXT),
    EMAIL(StorageClass.TEXT),
    PHONE(StorageClass.TEXT),
    USER(StorageClass.USER);

    private final StorageClass storageClass;

    FieldType(StorageClass storageClass) {
        this.storageClass = storageClass;
    }

    public StorageClass storageClass() {
        return storageClass;
    }

    public boolean compatibleWith(FieldType other) {
        return other != null && storageClass == other.storageClass;
    }

    public String stored() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FieldType fromStored(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Field type is required");
        }
        for (FieldType type : values()) {
            if (type.stored().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown field type: " + value);
    }
}
