package com.opencrm.document.repository;

import com.opencrm.document.domain.DocumentRendition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DocumentRenditionRepository extends JpaRepository<DocumentRendition, Long> {
    Optional<DocumentRendition> findByIdAndTenantIdAndDeletedFalse(Long id, String tenantId);
}
