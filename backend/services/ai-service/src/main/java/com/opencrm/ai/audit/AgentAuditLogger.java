package com.opencrm.ai.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AgentAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AgentAuditLogger.class);

    public void logInvocation(String agentId, String sessionId, Map<String, Object> inputContext) {
        log.info("Agent Invoked | Agent: {} | Session: {} | Context: {}", agentId, sessionId, inputContext);
        // TODO: Persist to Postgres ai_audit_log table
    }

    public void logResponse(String agentId, String sessionId, String llmOutput) {
        log.info("Agent Responded | Agent: {} | Session: {} | OutputLength: {}", agentId, sessionId, llmOutput.length());
        // TODO: Persist to Postgres ai_audit_log table
    }
}
