package com.opencrm.crm.fact;

import java.util.Optional;

/**
 * Reads and writes the few standard columns this slice knows how to apply.
 * Custom field values stay in the ledger until they are projected onto {@code field_value}.
 * Every call is tenant-scoped: the subject must belong to {@code tenant}.
 */
public interface RecordFieldAccess {

    void requireSubject(FactSubjectType subjectType, long subjectId, String tenant);

    Optional<ColumnState> read(FactSubjectType subjectType, long subjectId, String fieldKey, String tenant);

    /**
     * Copies an accepted or auto-applied value onto the subject.
     * Re-parent keys throw. Unmapped keys are a no-op.
     *
     * @return true when a column changed
     */
    boolean write(FactSubjectType subjectType, long subjectId, String fieldKey, String value, String tenant);
}
