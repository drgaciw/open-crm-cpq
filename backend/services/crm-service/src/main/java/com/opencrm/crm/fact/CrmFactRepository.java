package com.opencrm.crm.fact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CrmFactRepository extends JpaRepository<CrmFact, Long> {

    @Query("""
            SELECT DISTINCT f FROM CrmFact f
            LEFT JOIN FETCH f.evidence
            WHERE f.id = :id
              AND f.deleted = false
            """)
    Optional<CrmFact> findActiveById(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT f FROM CrmFact f
            LEFT JOIN FETCH f.evidence
            WHERE f.tenantId = :tenantId
              AND f.subjectType = :subjectType
              AND f.subjectId = :subjectId
              AND f.fieldKey = :fieldKey
              AND f.supersededAt IS NULL
              AND f.deleted = false
            """)
    List<CrmFact> findCurrent(
            @Param("tenantId") String tenantId,
            @Param("subjectType") FactSubjectType subjectType,
            @Param("subjectId") String subjectId,
            @Param("fieldKey") String fieldKey);

    @Query("""
            SELECT DISTINCT f FROM CrmFact f
            LEFT JOIN FETCH f.evidence
            WHERE f.tenantId = :tenantId
              AND f.subjectType = :subjectType
              AND f.subjectId = :subjectId
              AND f.supersededAt IS NULL
              AND f.deleted = false
              AND f.status IN :statuses
            """)
    List<CrmFact> findInbox(
            @Param("tenantId") String tenantId,
            @Param("subjectType") FactSubjectType subjectType,
            @Param("subjectId") String subjectId,
            @Param("statuses") Collection<FactStatus> statuses);
}
