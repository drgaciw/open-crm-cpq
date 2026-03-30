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

    private final AiAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AgentAuditLogger(AiAuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void logInvocation(String agentId, String sessionId, Map<String, Object> inputContext) {
        log.info("Agent Invoked | Agent: {} | Session: {} | Context: {}", agentId, sessionId, inputContext);

        String contextJson = null;
        try {
            if (inputContext != null) {
                contextJson = objectMapper.writeValueAsString(inputContext);
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize input context for audit log: {}", e.getMessage());
            contextJson = "{\"error\": \"Failed to serialize context\"}";
        }

        AiAuditLog auditLog = AiAuditLog.builder()
                .agentId(agentId)
                .sessionId(sessionId)
                .action("INVOCATION")
                .inputContext(contextJson)
                .build();

        auditLogRepository.save(auditLog);
    }

    public void logResponse(String agentId, String sessionId, String llmOutput) {
        log.info("Agent Responded | Agent: {} | Session: {} | OutputLength: {}", agentId, sessionId, llmOutput != null ? llmOutput.length() : 0);

        AiAuditLog auditLog = AiAuditLog.builder()
                .agentId(agentId)
                .sessionId(sessionId)
                .action("RESPONSE")
                .llmOutput(llmOutput)
                .build();

        auditLogRepository.save(auditLog);
    }
}
