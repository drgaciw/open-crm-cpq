package com.opencrm.quote.lifecycle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LifecycleChangeRepository extends JpaRepository<LifecycleChange, UUID> {

    Optional<LifecycleChange> findByIdAndTid(UUID id, String tid);

    Optional<LifecycleChange> findByTidAndIdempotencyKey(String tid, String idempotencyKey);

    /** Atomic compare-and-increment: callers must reload after the persistence context is cleared. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update LifecycleChange c set c.version = c.version + 1 where c.id = :id and c.tid = :tid and c.version = :version")
    int advanceVersion(@Param("id") UUID id, @Param("tid") String tid, @Param("version") long version);
}
