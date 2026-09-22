package com.opencrm.ai.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_audit_log", schema = "ai")
public class AiAuditLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "input_context", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String inputContext;

    @Column(name = "llm_output", columnDefinition = "TEXT")
    private String llmOutput;

    @Column(name = "human_decision")
    private String humanDecision;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "model")
    private String model;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "snapshot_references", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String snapshotReferences;

    public AiAuditLog() {
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (model == null) {
            model = "unknown";
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getInputContext() {
        return inputContext;
    }

    public void setInputContext(String inputContext) {
        this.inputContext = inputContext;
    }

    public String getLlmOutput() {
        return llmOutput;
    }

    public void setLlmOutput(String llmOutput) {
        this.llmOutput = llmOutput;
    }

    public String getHumanDecision() {
        return humanDecision;
    }

    public void setHumanDecision(String humanDecision) {
        this.humanDecision = humanDecision;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getSnapshotReferences() {
        return snapshotReferences;
    }

    public void setSnapshotReferences(String snapshotReferences) {
        this.snapshotReferences = snapshotReferences;
    }
}
