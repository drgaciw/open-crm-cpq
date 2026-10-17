package com.opencrm.ai.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.audit.entity.AiAuditLog;
import com.opencrm.ai.audit.repository.AiAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AgentAuditLogger.class);

    /**
     * Keys merged into {@code snapshot_references} for correlation when callers omit an explicit
     * {@code snapshotReferences} map — keeps audits usable for replay without stuffing full payloads.
     */
    private static final List<String> CONTEXT_CORRELATION_KEYS = List.of(
            "quoteId",
            "customerId",
            "configurationSessionId",
            "opportunityId",
            "dealId",
            "priceBookId",
            "agreementId",
            "quoteSnapshotId",
            "priceBookSnapshotId",
            "configurationSnapshotId"
    );

    private final AiAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AgentAuditLogger(AiAuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void logInvocation(String agentId, String sessionId, Map<String, Object> inputContext) {
        log.info("Agent Invoked | Agent: {} | Session: {}", agentId, sessionId);

        try {
            AiAuditLog auditLog = new AiAuditLog();
            auditLog.setAgentId(agentId);
            auditLog.setSessionId(sessionId);
            auditLog.setEventType("INVOCATION");
            if (inputContext != null && !inputContext.isEmpty()) {
                auditLog.setInputContext(objectMapper.writeValueAsString(inputContext));
                auditLog.setSnapshotReferences(serializeSnapshotReferences(inputContext));
            }
            auditLogRepository.save(auditLog);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize input context for audit log", e);
        }
    }

    public void logResponse(String agentId, String sessionId, String llmOutput) {
        logResponse(agentId, sessionId, llmOutput, null, null);
    }

    public void logResponse(String agentId, String sessionId, String llmOutput, Integer latencyMs, String model) {
        log.info("Agent Responded | Agent: {} | Session: {} | OutputLength: {}", agentId, sessionId,
                llmOutput != null ? llmOutput.length() : 0);

        AiAuditLog auditLog = new AiAuditLog();
        auditLog.setAgentId(agentId);
        auditLog.setSessionId(sessionId);
        auditLog.setEventType("RESPONSE");
        auditLog.setLlmOutput(llmOutput);
        auditLog.setLatencyMs(latencyMs);
        if (model != null && !model.isBlank()) {
            auditLog.setModel(model);
        }
        auditLogRepository.save(auditLog);
    }

    public void logFailure(String agentId, String sessionId, String errorMessage) {
        log.warn("Agent Failed | Agent: {} | Session: {} | Error: {}", agentId, sessionId, errorMessage);

        AiAuditLog auditLog = new AiAuditLog();
        auditLog.setAgentId(agentId);
        auditLog.setSessionId(sessionId);
        auditLog.setEventType("FAILURE");
        auditLog.setLlmOutput(errorMessage);
        auditLogRepository.save(auditLog);
    }

    private String serializeSnapshotReferences(Map<String, Object> inputContext) throws JsonProcessingException {
        Map<String, Object> merged = new LinkedHashMap<>();
        Object explicit = inputContext.get("snapshotReferences");
        if (explicit instanceof Map<?, ?> raw) {
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                merged.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        for (String key : CONTEXT_CORRELATION_KEYS) {
            if (inputContext.containsKey(key) && inputContext.get(key) != null) {
                merged.putIfAbsent(key, inputContext.get(key));
            }
        }
        return merged.isEmpty() ? null : objectMapper.writeValueAsString(merged);
    }
}
