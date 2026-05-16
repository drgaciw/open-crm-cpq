package com.opencrm.ai.audit.repository;

import com.opencrm.ai.audit.entity.AiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, UUID> {

    List<AiAuditLog> findByAgentIdOrderByCreatedAtDesc(String agentId);

    List<AiAuditLog> findBySessionIdOrderByCreatedAtDesc(String sessionId);
}
