package com.opencrm.ai.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.audit.entity.AiAuditLog;
import com.opencrm.ai.audit.repository.AiAuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentAuditLogger")
class AgentAuditLoggerTest {

    @Mock
    private AiAuditLogRepository auditLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AgentAuditLogger agentAuditLogger;

    @Captor
    private ArgumentCaptor<AiAuditLog> auditLogCaptor;

    @Test
    @DisplayName("logInvocation() persists an AiAuditLog with INVOCATION event type")
    void logInvocationPersistsAuditLog() throws Exception {
        // Arrange
        String agentId = "agent-001";
        String sessionId = "session-abc";
        Map<String, Object> inputContext = Map.of("productId", "PROD-1", "customerId", "CUST-1");
        String serializedContext = "{\"productId\":\"PROD-1\",\"customerId\":\"CUST-1\"}";

        when(objectMapper.writeValueAsString(inputContext)).thenReturn(serializedContext);

        // Act
        agentAuditLogger.logInvocation(agentId, sessionId, inputContext);

        // Assert
        verify(auditLogRepository).save(auditLogCaptor.capture());
        AiAuditLog saved = auditLogCaptor.getValue();

        assertEquals(agentId, saved.getAgentId());
        assertEquals(sessionId, saved.getSessionId());
        assertEquals("INVOCATION", saved.getEventType());
        assertEquals(serializedContext, saved.getEventData());
    }

    @Test
    @DisplayName("logInvocation() with null context map does not throw")
    void logInvocationWithNullContextDoesNotThrow() throws Exception {
        // Arrange
        when(objectMapper.writeValueAsString(null))
                .thenThrow(new JsonProcessingException("null input") {});

        // Act — should swallow the serialization error gracefully
        assertDoesNotThrow(() ->
                agentAuditLogger.logInvocation("agent-001", "session-xyz", null)
        );

        // The repository save must NOT be called if serialization fails
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("logResponse() persists an AiAuditLog with RESPONSE event type")
    void logResponsePersistsAuditLog() {
        // Arrange
        String agentId = "agent-002";
        String sessionId = "session-def";
        String llmOutput = "The recommended discount is 15%.";

        // Act
        agentAuditLogger.logResponse(agentId, sessionId, llmOutput);

        // Assert
        verify(auditLogRepository).save(auditLogCaptor.capture());
        AiAuditLog saved = auditLogCaptor.getValue();

        assertEquals(agentId, saved.getAgentId());
        assertEquals(sessionId, saved.getSessionId());
        assertEquals("RESPONSE", saved.getEventType());
        assertEquals(llmOutput, saved.getEventData());
    }

    @Test
    @DisplayName("repository save is called once per logInvocation call")
    void repositorySaveCalledOncePerInvocation() throws Exception {
        // Arrange
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // Act
        agentAuditLogger.logInvocation("a1", "s1", Map.of("k", "v"));
        agentAuditLogger.logInvocation("a2", "s2", Map.of("x", "y"));

        // Assert
        verify(auditLogRepository, times(2)).save(any(AiAuditLog.class));
    }

    @Test
    @DisplayName("logResponse() save is isolated per call — repository interaction verified per agentId")
    void logResponseSaveIsCalledForEachInvocation() {
        // Arrange
        List<String> agentIds = List.of("agent-A", "agent-B", "agent-C");

        // Act
        agentIds.forEach(id ->
                agentAuditLogger.logResponse(id, "session", "output for " + id)
        );

        // Assert — each call triggers one save, three agents → three saves
        verify(auditLogRepository, times(3)).save(any(AiAuditLog.class));
    }
}
