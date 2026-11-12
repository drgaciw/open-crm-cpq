package com.opencrm.document.repository;

import com.opencrm.document.domain.RenditionSelectedClause;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RenditionSelectedClauseRepository extends JpaRepository<RenditionSelectedClause, Long> {
    List<RenditionSelectedClause> findByRenditionIdAndTenantIdOrderBySortOrderAscClauseKeyAsc(Long renditionId, String tenantId);
}
