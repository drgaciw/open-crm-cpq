package com.opencrm.jev.shadow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * Writes to the {@code jev_decision_log} table each participating service owns in its own schema
 * (ADR-008). Plain JDBC, no entity, so JPA schema validation and entity scanning are unaffected. Called
 * from the advisory runner thread, outside any request transaction.
 */
public class JdbcJevDecisionLogStore implements JevDecisionLogStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcJevDecisionLogStore.class);

    static final String INSERT = """
            INSERT INTO jev_decision_log (
                use_case, subject_type, subject_id, question_id, question_type,
                evaluator_model, rubric_version, transport, wire_format,
                feature_schema, feature_schema_version, features_hash, features,
                answer_value, confidence, probabilities,
                authoritative_outcome, authoritative_source, agreement,
                status, error_class, latency_ms, attempts, usage
            ) VALUES (
                :useCase, :subjectType, :subjectId, :questionId, :questionType,
                :evaluatorModel, :rubricVersion, :transport, :wireFormat,
                :featureSchema, :featureSchemaVersion, :featuresHash, CAST(:features AS jsonb),
                :answerValue, :confidence, CAST(:probabilities AS jsonb),
                :authoritativeOutcome, :authoritativeSource, :agreement,
                :status, :errorClass, :latencyMs, :attempts, CAST(:usage AS jsonb)
            )""";

    static final String FIND_LATEST = """
            SELECT status, answer_value, confidence, probabilities, evaluator_model, rubric_version, created_at
            FROM jev_decision_log
            WHERE use_case = :useCase AND features_hash = :featuresHash
            ORDER BY created_at DESC, id DESC
            LIMIT 1""";

    static final String FIND_AWAITING = """
            SELECT DISTINCT subject_id
            FROM jev_decision_log
            WHERE use_case = :useCase AND subject_type = :subjectType
              AND authoritative_outcome IS NULL AND answer_value IS NOT NULL
            LIMIT :limit""";

    static final String RECORD_OUTCOME = """
            UPDATE jev_decision_log
            SET authoritative_outcome = :outcome, authoritative_source = :source,
                agreement = (answer_value = :outcome)
            WHERE use_case = :useCase AND subject_type = :subjectType AND subject_id = :subjectId
              AND authoritative_outcome IS NULL AND answer_value IS NOT NULL""";

    private final NamedParameterJdbcOperations jdbc;

    public JdbcJevDecisionLogStore(NamedParameterJdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(JevDecisionRecord record) {
        try {
            jdbc.update(INSERT, new MapSqlParameterSource()
                    .addValue("useCase", record.useCase())
                    .addValue("subjectType", record.subjectType())
                    .addValue("subjectId", record.subjectId())
                    .addValue("questionId", record.questionId())
                    .addValue("questionType", record.questionType())
                    .addValue("evaluatorModel", record.evaluatorModel())
                    .addValue("rubricVersion", record.rubricVersion())
                    .addValue("transport", record.transport())
                    .addValue("wireFormat", record.wireFormat())
                    .addValue("featureSchema", record.featureSchema())
                    .addValue("featureSchemaVersion", record.featureSchemaVersion())
                    .addValue("featuresHash", record.featuresHash())
                    .addValue("features", record.featuresJson())
                    .addValue("answerValue", record.answerValue())
                    .addValue("confidence", record.confidence())
                    .addValue("probabilities", record.probabilitiesJson())
                    .addValue("authoritativeOutcome", record.authoritativeOutcome())
                    .addValue("authoritativeSource", record.authoritativeSource().name())
                    .addValue("agreement", record.agreement())
                    .addValue("status", record.status())
                    .addValue("errorClass", record.errorClass())
                    .addValue("latencyMs", record.latencyMs())
                    .addValue("attempts", record.attempts())
                    .addValue("usage", record.usageJson()));
        } catch (RuntimeException e) {
            log.warn("Could not record Jev {} decision: {}", record.useCase(), e.getMessage());
        }
    }

    @Override
    public Optional<StoredDecision> findLatest(String useCase, String featuresHash) {
        try {
            List<StoredDecision> rows = jdbc.query(FIND_LATEST, new MapSqlParameterSource()
                            .addValue("useCase", useCase)
                            .addValue("featuresHash", featuresHash),
                    (rs, rowNum) -> {
                        Timestamp createdAt = rs.getTimestamp("created_at");
                        java.math.BigDecimal confidence = rs.getBigDecimal("confidence");
                        return new StoredDecision(rs.getString("status"), rs.getString("answer_value"),
                                confidence == null ? null : confidence.doubleValue(),
                                rs.getString("probabilities"),
                                rs.getString("evaluator_model"), rs.getString("rubric_version"),
                                createdAt == null ? null : createdAt.toInstant());
                    });
            return rows.stream().findFirst();
        } catch (RuntimeException e) {
            log.warn("Could not read Jev {} decision: {}", useCase, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<String> findSubjectsAwaitingOutcome(String useCase, String subjectType, int limit) {
        try {
            return jdbc.queryForList(FIND_AWAITING, new MapSqlParameterSource()
                    .addValue("useCase", useCase)
                    .addValue("subjectType", subjectType)
                    .addValue("limit", limit), String.class);
        } catch (RuntimeException e) {
            log.warn("Could not list Jev {} decisions awaiting an outcome: {}", useCase, e.getMessage());
            return List.of();
        }
    }

    @Override
    public int recordOutcome(String useCase, String subjectType, String subjectId, String outcome,
                             JevDecisionRecord.AuthoritativeSource source) {
        try {
            return jdbc.update(RECORD_OUTCOME, new MapSqlParameterSource()
                    .addValue("useCase", useCase)
                    .addValue("subjectType", subjectType)
                    .addValue("subjectId", subjectId)
                    .addValue("outcome", outcome)
                    .addValue("source", source.name()));
        } catch (RuntimeException e) {
            log.warn("Could not record Jev {} outcome: {}", useCase, e.getMessage());
            return 0;
        }
    }
}
