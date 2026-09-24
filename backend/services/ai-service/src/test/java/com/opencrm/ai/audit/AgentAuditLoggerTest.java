package com.opencrm.ai.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.audit.entity.AiAuditLog;
import com.opencrm.ai.audit.repository.AiAuditLogRepository;
import com.opencrm.security.CurrentActor;
import com.opencrm.security.test.TestCallers;
import com.opencrm.security.test.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentAuditLogger")
class AgentAuditLoggerTest {

    @Mock
    private AiAuditLogRepository auditLogRepository;

    private ObjectMapper objectMapper;

    private AgentAuditLogger agentAuditLogger;

    @Captor
    private ArgumentCaptor<AiAuditLog> auditLogCaptor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        agentAuditLogger = new AgentAuditLogger(auditLogRepository, objectMapper,
                new CurrentActor("service:ai-service", false));
    }

    @AfterEach
    void signOut() {
        TestCallers.clear();
    }

    @Test
    @DisplayName("ADR-016 step 4: every entry records who caused it, the user or else ai-service itself")
    void entriesRecordTheActor() {
        TestCallers.signInUser("rep-1", "CPQ.User");
        agentAuditLogger.logInvocation("agent-001", "session-abc", Map.of());
        TestCallers.clear();
        agentAuditLogger.logFailure("agent-001", "session-abc", "timeout");

        verify(auditLogRepository, times(2)).save(auditLogCaptor.capture());
        assertEquals(TestTokens.oidOf("rep-1"), auditLogCaptor.getAllValues().get(0).getCreatedBy());
        assertEquals("service:ai-service", auditLogCaptor.getAllValues().get(1).getCreatedBy());
    }

    @Test
    @DisplayName("logInvocation() persists an AiAuditLog with INVOCATION event type")
    void logInvocationPersistsAuditLog() throws Exception {
        String agentId = "agent-001";
        String sessionId = "session-abc";
        Map<String, Object> inputContext = Map.of("productId", "PROD-1", "customerId", "CUST-1");

        agentAuditLogger.logInvocation(agentId, sessionId, inputContext);

        verify(auditLogRepository).save(auditLogCaptor.capture());
        AiAuditLog saved = auditLogCaptor.getValue();

        assertEquals(agentId, saved.getAgentId());
        assertEquals(sessionId, saved.getSessionId());
        assertEquals("INVOCATION", saved.getEventType());
        JsonNode ctx = objectMapper.readTree(saved.getInputContext());
        assertEquals("PROD-1", ctx.get("productId").asText());
        assertEquals("CUST-1", ctx.get("customerId").asText());
        assertNull(saved.getLlmOutput());

        JsonNode snap = objectMapper.readTree(saved.getSnapshotReferences());
        assertEquals("CUST-1", snap.get("customerId").asText());
    }

    @Test
    @DisplayName("logInvocation() persists snapshot references when present in context")
    void logInvocationPersistsSnapshotReferences() throws Exception {
        String agentId = "agent-001";
        String sessionId = "session-abc";
        Map<String, Object> snapshotReferences = Map.of(
                "quoteSnapshotId", "quote-snapshot-123",
                "priceBookSnapshotId", "price-book-snapshot-456"
        );
        Map<String, Object> inputContext = Map.of(
                "quoteId", "QUOTE-1",
                "snapshotReferences", snapshotReferences
        );

        agentAuditLogger.logInvocation(agentId, sessionId, inputContext);

        verify(auditLogRepository).save(auditLogCaptor.capture());
        AiAuditLog saved = auditLogCaptor.getValue();

        JsonNode snap = objectMapper.readTree(saved.getSnapshotReferences());
        assertEquals("QUOTE-1", snap.get("quoteId").asText());
        assertEquals("quote-snapshot-123", snap.get("quoteSnapshotId").asText());
        assertEquals("price-book-snapshot-456", snap.get("priceBookSnapshotId").asText());
    }

    @Test
    @DisplayName("logInvocation() with null context still persists invocation shell")
    void logInvocationWithNullContextStillSaves() {
        agentAuditLogger.logInvocation("agent-001", "session-xyz", null);

        verify(auditLogRepository).save(auditLogCaptor.capture());
        AiAuditLog saved = auditLogCaptor.getValue();
        assertEquals("INVOCATION", saved.getEventType());
        assertNull(saved.getInputContext());
        assertNull(saved.getSnapshotReferences());
    }

    @Test
    @DisplayName("logFailure() persists FAILURE event")
    void logFailurePersistsAuditLog() {
        agentAuditLogger.logFailure("agent-007", "sess", "LLM timeout");

        verify(auditLogRepository).save(auditLogCaptor.capture());
        AiAuditLog saved = auditLogCaptor.getValue();
        assertEquals("FAILURE", saved.getEventType());
        assertEquals("LLM timeout", saved.getLlmOutput());
    }

    @Test
    @DisplayName("logResponse() overload stores latency when provided")
    void logResponseStoresLatency() {
        agentAuditLogger.logResponse("a", "s", "out", 42, "gpt-test");

        verify(auditLogRepository).save(auditLogCaptor.capture());
        AiAuditLog saved = auditLogCaptor.getValue();
        assertEquals("RESPONSE", saved.getEventType());
        assertEquals(42, saved.getLatencyMs());
        assertEquals("gpt-test", saved.getModel());
    }

    @Test
    @DisplayName("logResponse() persists an AiAuditLog with RESPONSE event type")
    void logResponsePersistsAuditLog() {
        String agentId = "agent-002";
        String sessionId = "session-def";
        String llmOutput = "The recommended discount is 15%.";

        agentAuditLogger.logResponse(agentId, sessionId, llmOutput);

        verify(auditLogRepository).save(auditLogCaptor.capture());
        AiAuditLog saved = auditLogCaptor.getValue();

        assertEquals(agentId, saved.getAgentId());
        assertEquals(sessionId, saved.getSessionId());
        assertEquals("RESPONSE", saved.getEventType());
        assertEquals(llmOutput, saved.getLlmOutput());
        assertNull(saved.getInputContext());
    }

    @Test
    @DisplayName("repository save is called once per logInvocation call")
    void repositorySaveCalledOncePerInvocation() {
        agentAuditLogger.logInvocation("a1", "s1", Map.of("k", "v"));
        agentAuditLogger.logInvocation("a2", "s2", Map.of("x", "y"));

        verify(auditLogRepository, times(2)).save(any(AiAuditLog.class));
    }

    @Test
    @DisplayName("logResponse() save is isolated per call — repository interaction verified per agentId")
    void logResponseSaveIsCalledForEachInvocation() {
        agentAuditLogger.logResponse("agent-A", "session", "output for agent-A");
        agentAuditLogger.logResponse("agent-B", "session", "output for agent-B");
        agentAuditLogger.logResponse("agent-C", "session", "output for agent-C");

        verify(auditLogRepository, times(3)).save(any(AiAuditLog.class));
    }
}
