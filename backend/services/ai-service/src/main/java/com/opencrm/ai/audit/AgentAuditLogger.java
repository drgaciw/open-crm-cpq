package com.opencrm.ai.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AgentAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AgentAuditLogger.class);

    private final AiAuditLogRepository auditLogRepository;

    public AgentAuditLogger(AiAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void logInvocation(String agentId, String sessionId, Map<String, Object> inputContext) {
        log.info("Agent Invoked | Agent: {} | Session: {} | Context: {}", agentId, sessionId, inputContext);

        AiAuditLog auditLog = AiAuditLog.builder()
                .agentId(agentId)
                .sessionId(sessionId)
                .inputContext(inputContext)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void logResponse(String agentId, String sessionId, String llmOutput) {
        log.info("Agent Responded | Agent: {} | Session: {} | OutputLength: {}", agentId, sessionId, llmOutput.length());

        AiAuditLog auditLog = AiAuditLog.builder()
                .agentId(agentId)
                .sessionId(sessionId)
                .llmOutput(llmOutput)
                .build();

        auditLogRepository.save(auditLog);
    }
}
