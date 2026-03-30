package com.opencrm.ai.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

class AgentAuditLoggerTest {

    @Mock
    private AiAuditLogRepository auditLogRepository;

    private AgentAuditLogger agentAuditLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agentAuditLogger = new AgentAuditLogger(auditLogRepository);
    }

    @Test
    void logInvocation_ShouldSaveAuditLog() {
        String agentId = "agent-1";
        String sessionId = "session-1";
        Map<String, Object> inputContext = Map.of("key", "value");

        agentAuditLogger.logInvocation(agentId, sessionId, inputContext);

        ArgumentCaptor<AiAuditLog> captor = ArgumentCaptor.forClass(AiAuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AiAuditLog savedLog = captor.getValue();
        assertEquals(agentId, savedLog.getAgentId());
        assertEquals(sessionId, savedLog.getSessionId());
        assertEquals(inputContext, savedLog.getInputContext());
    }

    @Test
    void logResponse_ShouldSaveAuditLog() {
        String agentId = "agent-1";
        String sessionId = "session-1";
        String llmOutput = "output";

        agentAuditLogger.logResponse(agentId, sessionId, llmOutput);

        ArgumentCaptor<AiAuditLog> captor = ArgumentCaptor.forClass(AiAuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AiAuditLog savedLog = captor.getValue();
        assertEquals(agentId, savedLog.getAgentId());
        assertEquals(sessionId, savedLog.getSessionId());
        assertEquals(llmOutput, savedLog.getLlmOutput());
    }
}
