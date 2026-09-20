package com.opencrm.jev.shadow;

/** Persists shadow decisions. Implementations must never throw into the caller. */
public interface JevDecisionLogStore {

    void save(JevDecisionRecord record);

    /** Used when a service has no DataSource; decisions are simply not recorded. */
    JevDecisionLogStore NONE = record -> { };
}
