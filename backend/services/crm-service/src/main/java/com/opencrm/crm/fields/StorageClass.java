package com.opencrm.crm.fields;

/**
 * One physical column family on {@code field_value}. Types share a class when a value can stay in that column.
 */
public enum StorageClass {
    TEXT,
    NUMERIC,
    DATE,
    BOOL,
    OPTION,
    USER
}
