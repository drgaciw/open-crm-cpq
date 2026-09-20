package com.opencrm.jev.shadow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

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
}
