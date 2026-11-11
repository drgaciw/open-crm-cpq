package com.opencrm.document.repository;

import com.opencrm.document.domain.ClauseDefinition;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ClauseDefinitionRepository extends JpaRepository<ClauseDefinition, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClauseDefinition c where c.id = :id and c.tenantId = :tenant and c.deleted = false")
    Optional<ClauseDefinition> lockByTenant(@Param("id") Long id, @Param("tenant") String tenant);
    boolean existsByTenantIdAndClauseKey(String tenantId, String clauseKey);
}
