package com.opencrm.crm.fact;

/**
 * A subject column the ledger might overwrite. {@code lastWriter} is the record's
 * {@code updatedBy}, or {@code createdBy} when the row was never updated.
 * A blank writer with a present value is treated as a human.
 */
public record ColumnState(String value, String lastWriter) {
}
