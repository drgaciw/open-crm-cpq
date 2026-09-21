package com.opencrm.jev.shadow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persists shadow decisions. Implementations must never throw into the caller. */
public interface JevDecisionLogStore {

    void save(JevDecisionRecord record);

    /**
     * Newest decision for the same use case and feature state, whatever its status. Lets a caller reuse an
     * answer instead of paying for the same question twice.
     */
    default Optional<StoredDecision> findLatest(String useCase, String featuresHash) {
        return Optional.empty();
    }

    /** Subjects whose decisions still have no authoritative outcome to compare against. */
    default List<String> findSubjectsAwaitingOutcome(String useCase, String subjectType, int limit) {
        return List.of();
    }

    /**
     * Fill in an authoritative outcome that only became known later (for example the tier a user picked)
     * and compute agreement. Rows that already have an outcome are left alone.
     *
     * @return rows updated
     */
    default int recordOutcome(String useCase, String subjectType, String subjectId, String outcome,
                              JevDecisionRecord.AuthoritativeSource source) {
        return 0;
    }

    /** What a caller needs to present or reuse a stored decision. */
    record StoredDecision(String status, String answerValue, Double confidence, String probabilitiesJson,
                          String evaluatorModel, String rubricVersion, Instant createdAt) {
    }

    /** Used when a service has no DataSource; decisions are simply not recorded. */
    JevDecisionLogStore NONE = record -> { };
}
