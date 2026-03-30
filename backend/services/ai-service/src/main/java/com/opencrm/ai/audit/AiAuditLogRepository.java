package com.opencrm.ai.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, Long> {
}
