package com.opencrm.order.advisory;

import com.opencrm.jev.shadow.JevDecisionRecord;
import com.opencrm.jev.state.JevState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.opencrm.order.advisory.OrderAdvisoryFeatures.*;

/** Advisory-only storage. No business rows are mutated or locked. */
@Repository
public class OrderAdvisoryRepository {
    private final JdbcTemplate db;
    private final NamedParameterJdbcTemplate named;
    private final TransactionTemplate tx;

    public OrderAdvisoryRepository(JdbcTemplate db, PlatformTransactionManager manager) {
        this.db = new JdbcTemplate(java.util.Objects.requireNonNull(db.getDataSource()));
        this.db.setQueryTimeout(5);
        this.named = new NamedParameterJdbcTemplate(this.db);
        this.tx = new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tx.setTimeout(5);
    }

    /** One statement gives a consistent committed view, bounded to five exceptions per trigger. */
    public List<OrderAdvisorySnapshot> snapshots(String orderId, String scope, Instant now) {
        return db.query("""
                SELECT o.scope_id,o.customer_id,o.id,o.version,e.id AS exception_id,e.version AS exception_version,
                       e.category,e.severity,e.first_occurred_at,
                       (EXISTS(SELECT 1 FROM task_operations t WHERE t.order_id=o.id AND t.status='UNKNOWN')
                         OR EXISTS(SELECT 1 FROM order_tasks t WHERE t.order_id=o.id AND t.status='UNKNOWN')) AS unknown_outcome,
                       EXISTS(SELECT 1 FROM fulfillment_evidence f JOIN fulfillment_evidence_state s ON s.evidence_id=f.id
                              WHERE f.order_id=o.id AND s.status='PENDING') AS pending_evidence,
                       EXISTS(SELECT 1 FROM fulfillment_evidence f JOIN fulfillment_evidence_state s ON s.evidence_id=f.id
                              WHERE f.order_id=o.id AND s.status='CONFLICT'
                              AND NOT EXISTS(SELECT 1 FROM recovery_evidence_adjudications a WHERE a.evidence_id=f.id)) AS conflict_evidence,
                       EXISTS(SELECT 1 FROM order_tasks t WHERE t.order_id=o.id AND t.kind='ACTIVATION'
                              AND t.status NOT IN ('SUCCESS','CANCELLED')) AS activation_pending,
                       o.finance_status <> 'ACKNOWLEDGED' AS finance_pending
                FROM orders o JOIN order_exceptions e ON e.order_id=o.id
                WHERE o.id=? AND o.scope_id=? AND NOT o.is_deleted AND e.status='OPEN'
                ORDER BY e.first_occurred_at,e.id LIMIT 5
                """, (rs, n) -> new OrderAdvisorySnapshot(rs.getString("scope_id"), rs.getString("customer_id"),
                rs.getString("id"), rs.getLong("version"), rs.getString("exception_id"), rs.getLong("exception_version"), now,
                new Facts(Category.from(rs.getString("category")), Severity.from(rs.getString("severity")),
                        AgeBand.from(Duration.between(rs.getTimestamp("first_occurred_at").toInstant(), now)),
                        Flag.of(rs.getBoolean("unknown_outcome")), Flag.of(rs.getBoolean("pending_evidence")),
                        Flag.of(rs.getBoolean("conflict_evidence")), Flag.of(rs.getBoolean("activation_pending")),
                        Flag.of(rs.getBoolean("finance_pending")))), orderId, scope);
    }

    public boolean claim(UUID id, String key, OrderAdvisorySnapshot s, JevState state,
                         String model, String rubric, Instant expiresAt) {
        return db.update("""
                INSERT INTO order_advisory_claims(id,dedup_key,scope_id,customer_id,order_id,order_version,
                    exception_id,exception_version,features_hash,evaluator_model,rubric_version,created_at,expires_at)
                SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?
                WHERE EXISTS(SELECT 1 FROM orders o JOIN order_exceptions e ON e.order_id=o.id
                    WHERE o.id=? AND o.scope_id=? AND o.customer_id=? AND o.version=? AND NOT o.is_deleted
                    AND e.id=? AND e.version=? AND e.status='OPEN')
                ON CONFLICT(dedup_key) DO NOTHING
                """, id, key, s.scopeId(), s.customerId(), s.orderId(), s.orderVersion(), s.exceptionId(),
                s.exceptionVersion(), state.sha256(), model, rubric, Timestamp.from(s.capturedAt()), Timestamp.from(expiresAt),
                s.orderId(), s.scopeId(), s.customerId(), s.orderVersion(), s.exceptionId(), s.exceptionVersion()) == 1;
    }

    /** Both rows commit together. Never use the shared store's swallowing save for this invariant. */
    public void complete(UUID claimId, JevDecisionRecord r) {
        tx.executeWithoutResult(status -> {
            UUID decisionId = UUID.randomUUID();
            named.update("""
                    INSERT INTO jev_decision_log(uuid,use_case,subject_type,subject_id,question_id,question_type,
                      evaluator_model,rubric_version,transport,wire_format,feature_schema,feature_schema_version,
                      features_hash,features,answer_value,confidence,probabilities,authoritative_outcome,
                      authoritative_source,agreement,status,error_class,latency_ms,attempts,usage)
                    VALUES(:uuid,:useCase,:subjectType,:subjectId,:questionId,:questionType,:model,:rubric,:transport,
                      :wire,:schema,:schemaVersion,:hash,CAST(:features AS jsonb),:answer,:confidence,
                      CAST(:probabilities AS jsonb),:outcome,:source,:agreement,:status,:error,:latency,:attempts,CAST(:usage AS jsonb))
                    """, new MapSqlParameterSource()
                    .addValue("uuid", decisionId).addValue("useCase", r.useCase()).addValue("subjectType", r.subjectType())
                    .addValue("subjectId", r.subjectId()).addValue("questionId", r.questionId()).addValue("questionType", r.questionType())
                    .addValue("model", r.evaluatorModel()).addValue("rubric", r.rubricVersion()).addValue("transport", r.transport())
                    .addValue("wire", r.wireFormat()).addValue("schema", r.featureSchema()).addValue("schemaVersion", r.featureSchemaVersion())
                    .addValue("hash", r.featuresHash()).addValue("features", r.featuresJson()).addValue("answer", r.answerValue())
                    .addValue("confidence", r.confidence()).addValue("probabilities", r.probabilitiesJson())
                    .addValue("outcome", r.authoritativeOutcome()).addValue("source", r.authoritativeSource().name())
                    .addValue("agreement", r.agreement()).addValue("status", r.status()).addValue("error", r.errorClass())
                    .addValue("latency", r.latencyMs()).addValue("attempts", r.attempts()).addValue("usage", r.usageJson()));
            db.update("INSERT INTO order_advisory_decisions(claim_id,decision_uuid) VALUES(?,?)", claimId, decisionId);
        });
    }
}
