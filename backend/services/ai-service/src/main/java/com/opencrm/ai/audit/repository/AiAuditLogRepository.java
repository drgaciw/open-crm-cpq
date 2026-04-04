package com.opencrm.ai.audit.repository;

import com.opencrm.ai.audit.entity.AiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, Long> {
}
