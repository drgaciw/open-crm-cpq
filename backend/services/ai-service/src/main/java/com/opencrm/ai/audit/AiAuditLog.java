package com.opencrm.ai.audit;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "ai_audit_log")
public class AiAuditLog extends BaseEntity {

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "input_context", columnDefinition = "TEXT")
    private String inputContext;

    @Column(name = "llm_output", columnDefinition = "TEXT")
    private String llmOutput;
}
