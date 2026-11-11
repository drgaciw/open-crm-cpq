package com.opencrm.ai.task;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTask, UUID> {

    /**
     * At most one unfinished row. The partial unique index is the authority; {@code LIMIT 1} keeps the read bounded.
     */
    @Query(nativeQuery = true, value = """
            SELECT t.* FROM {h-schema}agent_task t
            WHERE t.tenant_id = :tenantId
              AND t.kind = :kind
              AND t.subject_type = :subjectType
              AND t.subject_id = :subjectId
              AND t.finished_at IS NULL
            LIMIT 1
            """)
    Optional<AgentTask> findUnfinished(
            @Param("tenantId") String tenantId,
            @Param("kind") String kind,
            @Param("subjectType") String subjectType,
            @Param("subjectId") String subjectId);

    /**
     * Due, unfinished rows whose lease is absent or expired. {@code LIMIT} is the clamped batch size.
     * Must run inside the caller's transaction so the lock and the lease update commit together.
     */
    @Query(nativeQuery = true, value = """
            SELECT t.* FROM {h-schema}agent_task t
            WHERE t.due_at <= :now
              AND (t.leased_until IS NULL OR t.leased_until < :now)
              AND t.finished_at IS NULL
            ORDER BY t.priority DESC, t.due_at
            LIMIT :limit
            FOR UPDATE OF t SKIP LOCKED
            """)
    List<AgentTask> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AgentTask t where t.id = :id")
    Optional<AgentTask> lockById(@Param("id") UUID id);
}
