package com.opencrm.ai.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AgentAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AgentAuditLogger.class);

    private final AiAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AgentAuditLogger(AiAuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void logInvocation(String agentId, String sessionId, Map<String, Object> inputContext) {
        log.info("Agent Invoked | Agent: {} | Session: {} | Context: {}", agentId, sessionId, inputContext);

        String details = "{}";
        try {
            details = objectMapper.writeValueAsString(inputContext);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize inputContext for audit log", e);
        }

        AiAuditLog auditLog = AiAuditLog.builder()
                .agentId(agentId)
                .sessionId(sessionId)
                .action("INVOCATION")
                .details(details)
                .build();

        repository.save(auditLog);
    }

    public void logResponse(String agentId, String sessionId, String llmOutput) {
        log.info("Agent Responded | Agent: {} | Session: {} | OutputLength: {}", agentId, sessionId, llmOutput != null ? llmOutput.length() : 0);

        AiAuditLog auditLog = AiAuditLog.builder()
                .agentId(agentId)
                .sessionId(sessionId)
                .action("RESPONSE")
                .details(llmOutput)
                .build();

        repository.save(auditLog);
    }
}
