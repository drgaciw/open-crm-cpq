package com.opencrm.ai.audit;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "ai_audit_log", schema = "ai")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AiAuditLog extends BaseEntity {

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_context", columnDefinition = "jsonb")
    private Map<String, Object> inputContext;

    @Column(name = "llm_output", columnDefinition = "text")
    private String llmOutput;
}
