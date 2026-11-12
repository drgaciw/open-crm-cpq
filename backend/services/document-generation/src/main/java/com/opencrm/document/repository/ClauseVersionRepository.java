package com.opencrm.document.repository;

import com.opencrm.document.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ClauseVersionRepository extends JpaRepository<ClauseVersion, Long> {
    List<ClauseVersion> findByTenantIdAndDeletedFalseOrderByClauseKeyAscVersionNumberDesc(String tenantId);
    List<ClauseVersion> findByTenantIdAndStatusAndDeletedFalseOrderBySortOrderAscClauseKeyAscVersionNumberDesc(String tenantId, ClauseStatus status);
    Optional<ClauseVersion> findByIdAndTenantIdAndDeletedFalse(Long id, String tenantId);
    Optional<ClauseVersion> findFirstByClauseIdAndTenantIdOrderByVersionNumberDesc(Long clauseId, String tenantId);
}
