package com.opencrm.ai.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.audit.entity.AiAuditLog;
import com.opencrm.ai.audit.repository.AiAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AgentAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AgentAuditLogger.class);

    private final AiAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AgentAuditLogger(AiAuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void logInvocation(String agentId, String sessionId, Map<String, Object> inputContext) {
        log.info("Agent Invoked | Agent: {} | Session: {} | Context: {}", agentId, sessionId, inputContext);

        try {
            AiAuditLog auditLog = new AiAuditLog();
            auditLog.setAgentId(agentId);
            auditLog.setSessionId(sessionId);
            auditLog.setEventType("INVOCATION");
            auditLog.setEventData(objectMapper.writeValueAsString(inputContext));
            auditLog.setSnapshotReferences(serializeSnapshotReferences(inputContext));
            auditLogRepository.save(auditLog);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize input context for audit log", e);
        }
    }

    public void logResponse(String agentId, String sessionId, String llmOutput) {
        log.info("Agent Responded | Agent: {} | Session: {} | OutputLength: {}", agentId, sessionId, llmOutput.length());

        AiAuditLog auditLog = new AiAuditLog();
        auditLog.setAgentId(agentId);
        auditLog.setSessionId(sessionId);
        auditLog.setEventType("RESPONSE");
        auditLog.setEventData(llmOutput);
        auditLogRepository.save(auditLog);
    }

    private String serializeSnapshotReferences(Map<String, Object> inputContext) throws JsonProcessingException {
        if (inputContext == null || !inputContext.containsKey("snapshotReferences")) {
            return null;
        }

        return objectMapper.writeValueAsString(inputContext.get("snapshotReferences"));
    }
}
