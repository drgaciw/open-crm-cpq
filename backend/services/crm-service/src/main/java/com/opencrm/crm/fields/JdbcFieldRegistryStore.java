package com.opencrm.crm.fields;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcFieldRegistryStore implements FieldRegistryStore {

    private static final String DEFINITION_COLUMNS = """
            id, tenant, entity, "key", label, type, required, show_on_record, show_on_list, position,
            agent_filled, agent_brief, archived_at, created_by, updated_by, version
            """;

    private static final RowMapper<FieldRows.Definition> DEFINITION_MAPPER = (rs, rowNum) -> new FieldRows.Definition(
            rs.getLong("id"),
            rs.getString("tenant"),
            FieldEntityKind.fromStored(rs.getString("entity")),
            rs.getString("key"),
            rs.getString("label"),
            FieldType.fromStored(rs.getString("type")),
            rs.getBoolean("required"),
            rs.getBoolean("show_on_record"),
            rs.getBoolean("show_on_list"),
            rs.getInt("position"),
            rs.getBoolean("agent_filled"),
            rs.getString("agent_brief"),
            instant(rs, "archived_at"),
            rs.getString("created_by"),
            rs.getString("updated_by"),
            rs.getLong("version"));

    private static final RowMapper<FieldRows.Option> OPTION_MAPPER = (rs, rowNum) -> new FieldRows.Option(
            rs.getLong("id"),
            rs.getLong("definition_id"),
            rs.getString("label"),
            rs.getInt("position"),
            instant(rs, "archived_at"),
            rs.getString("created_by"),
            rs.getString("updated_by"),
            rs.getLong("version"));

    private static final RowMapper<FieldRows.Value> VALUE_MAPPER = (rs, rowNum) -> new FieldRows.Value(
            rs.getLong("id"),
            rs.getLong("definition_id"),
            nullableLong(rs, "organization_id"),
            nullableLong(rs, "contact_id"),
            nullableLong(rs, "deal_id"),
            rs.getString("text"),
            rs.getBigDecimal("numeric"),
            rs.getObject("date", LocalDate.class),
            rs.getObject("bool", Boolean.class),
            nullableLong(rs, "option_id"),
            rs.getString("user_id"),
            rs.getString("created_by"),
            rs.getString("updated_by"),
            rs.getLong("version"));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcFieldRegistryStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public FieldRows.Definition insertDefinition(FieldRows.Definition row) {
        String sql = """
                INSERT INTO crm.field_definition (
                    tenant, entity, "key", label, type, required, show_on_record, show_on_list, position,
                    agent_filled, agent_brief, created_by, updated_by
                ) VALUES (
                    :tenant, :entity, :key, :label, :type, :required, :showOnRecord, :showOnList, :position,
                    :agentFilled, :agentBrief, :actor, :actor
                )
                """;
        long id = insert(sql, definitionParams(row), "id");
        return findDefinition(row.tenant(), id).orElseThrow();
    }

    @Override
    public int insertDefinitionIfAbsent(FieldRows.Definition row) {
        String sql = """
                INSERT INTO crm.field_definition (
                    tenant, entity, "key", label, type, required, show_on_record, show_on_list, position,
                    agent_filled, agent_brief, created_by, updated_by
                ) VALUES (
                    :tenant, :entity, :key, :label, :type, :required, :showOnRecord, :showOnList, :position,
                    :agentFilled, :agentBrief, :actor, :actor
                )
                ON CONFLICT ON CONSTRAINT uq_field_definition_tenant_entity_key DO NOTHING
                """;
        return jdbc.update(sql, definitionParams(row));
    }

    @Override
    public Optional<FieldRows.Definition> findDefinition(String tenant, long id) {
        String sql = "SELECT " + DEFINITION_COLUMNS + """
                 FROM crm.field_definition WHERE tenant = :tenant AND id = :id
                """;
        List<FieldRows.Definition> rows = jdbc.query(sql, new MapSqlParameterSource()
                .addValue("tenant", tenant)
                .addValue("id", id), DEFINITION_MAPPER);
        return rows.stream().findFirst();
    }

    @Override
    public List<FieldRows.Definition> listDefinitions(String tenant, FieldEntityKind entity) {
        String sql = "SELECT " + DEFINITION_COLUMNS + """
                 FROM crm.field_definition
                 WHERE tenant = :tenant AND entity = :entity
                 ORDER BY position, "key", id
                """;
        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("tenant", tenant)
                .addValue("entity", entity.stored()), DEFINITION_MAPPER);
    }

    @Override
    public boolean updateDefinition(FieldRows.Definition row, String actor, long expectedVersion) {
        String sql = """
                UPDATE crm.field_definition
                SET label = :label,
                    type = :type,
                    required = :required,
                    show_on_record = :showOnRecord,
                    show_on_list = :showOnList,
                    position = :position,
                    agent_filled = :agentFilled,
                    agent_brief = :agentBrief,
                    updated_by = :actor,
                    updated_at = NOW(),
                    version = version + 1
                WHERE id = :id AND tenant = :tenant AND version = :version AND archived_at IS NULL
                """;
        MapSqlParameterSource params = definitionParams(row)
                .addValue("id", row.id())
                .addValue("actor", actor)
                .addValue("version", expectedVersion);
        return jdbc.update(sql, params) == 1;
    }

    @Override
    public boolean archiveDefinition(String tenant, long id, String actor, long expectedVersion) {
        String sql = """
                UPDATE crm.field_definition
                SET archived_at = NOW(),
                    updated_by = :actor,
                    updated_at = NOW(),
                    version = version + 1
                WHERE id = :id AND tenant = :tenant AND version = :version AND archived_at IS NULL
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenant", tenant)
                .addValue("actor", actor)
                .addValue("version", expectedVersion)) == 1;
    }

    @Override
    public FieldRows.Option insertOption(FieldRows.Option row) {
        String sql = """
                INSERT INTO crm.field_option (
                    definition_id, label, position, created_by, updated_by
                ) VALUES (
                    :definitionId, :label, :position, :actor, :actor
                )
                """;
        long id = insert(sql, new MapSqlParameterSource()
                .addValue("definitionId", row.definitionId())
                .addValue("label", row.label())
                .addValue("position", row.position())
                .addValue("actor", row.createdBy()), "id");
        return findOption(row.definitionId(), id).orElseThrow();
    }

    @Override
    public List<FieldRows.Option> listOptions(Collection<Long> definitionIds) {
        if (definitionIds == null || definitionIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT id, definition_id, label, position, archived_at, created_by, updated_by, version
                FROM crm.field_option
                WHERE definition_id IN (:ids)
                ORDER BY position, id
                """;
        return jdbc.query(sql, new MapSqlParameterSource("ids", definitionIds), OPTION_MAPPER);
    }

    @Override
    public Optional<FieldRows.Option> findOption(long definitionId, long optionId) {
        String sql = """
                SELECT id, definition_id, label, position, archived_at, created_by, updated_by, version
                FROM crm.field_option
                WHERE definition_id = :definitionId AND id = :id
                """;
        List<FieldRows.Option> rows = jdbc.query(sql, new MapSqlParameterSource()
                .addValue("definitionId", definitionId)
                .addValue("id", optionId), OPTION_MAPPER);
        return rows.stream().findFirst();
    }

    @Override
    public boolean updateOption(FieldRows.Option row, String actor, long expectedVersion) {
        String sql = """
                UPDATE crm.field_option
                SET label = :label,
                    position = :position,
                    updated_by = :actor,
                    updated_at = NOW(),
                    version = version + 1
                WHERE id = :id AND definition_id = :definitionId AND version = :version
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", row.id())
                .addValue("definitionId", row.definitionId())
                .addValue("label", row.label())
                .addValue("position", row.position())
                .addValue("actor", actor)
                .addValue("version", expectedVersion)) == 1;
    }

    @Override
    public boolean archiveOption(long definitionId, long optionId, String actor, long expectedVersion) {
        String sql = """
                UPDATE crm.field_option
                SET archived_at = NOW(),
                    updated_by = :actor,
                    updated_at = NOW(),
                    version = version + 1
                WHERE id = :id AND definition_id = :definitionId AND version = :version AND archived_at IS NULL
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", optionId)
                .addValue("definitionId", definitionId)
                .addValue("actor", actor)
                .addValue("version", expectedVersion)) == 1;
    }

    @Override
    public List<String> textValues(long definitionId) {
        return jdbc.queryForList("""
                SELECT "text" FROM crm.field_value
                WHERE definition_id = :id AND "text" IS NOT NULL
                """, new MapSqlParameterSource("id", definitionId), String.class);
    }

    @Override
    public void upsertValue(FieldRows.Value row) {
        String recordColumn = recordColumn(row);
        String conflict = switch (recordKind(row)) {
            case ORGANIZATION -> "(definition_id, organization_id) WHERE organization_id IS NOT NULL";
            case CONTACT -> "(definition_id, contact_id) WHERE contact_id IS NOT NULL";
            case DEAL -> "(definition_id, deal_id) WHERE deal_id IS NOT NULL";
        };
        String sql = """
                INSERT INTO crm.field_value (
                    definition_id, %s, "text", "numeric", "date", "bool", option_id, user_id,
                    created_by, updated_by
                ) VALUES (
                    :definitionId, :recordId, :text, :numeric, :date, :bool, :optionId, :userId,
                    :actor, :actor
                )
                ON CONFLICT %s
                DO UPDATE SET
                    "text" = EXCLUDED."text",
                    "numeric" = EXCLUDED."numeric",
                    "date" = EXCLUDED."date",
                    "bool" = EXCLUDED."bool",
                    option_id = EXCLUDED.option_id,
                    user_id = EXCLUDED.user_id,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = NOW(),
                    version = crm.field_value.version + 1
                """.formatted(recordColumn, conflict);
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("definitionId", row.definitionId())
                .addValue("recordId", recordId(row))
                .addValue("text", row.text())
                .addValue("numeric", row.numeric())
                .addValue("date", row.date())
                .addValue("bool", row.bool())
                .addValue("optionId", row.optionId())
                .addValue("userId", row.userId())
                .addValue("actor", row.createdBy()));
    }

    @Override
    public Optional<FieldRows.Value> findValue(long definitionId, FieldEntityKind entity, long recordId) {
        String sql = "SELECT id, definition_id, organization_id, contact_id, deal_id, \"text\", \"numeric\", \"date\", \"bool\", option_id, user_id, created_by, updated_by, version FROM crm.field_value WHERE definition_id = :definitionId AND "
                + recordColumn(entity) + " = :recordId";
        List<FieldRows.Value> rows = jdbc.query(sql, new MapSqlParameterSource()
                .addValue("definitionId", definitionId)
                .addValue("recordId", recordId), VALUE_MAPPER);
        return rows.stream().findFirst();
    }

    @Override
    public boolean deleteValue(long definitionId, FieldEntityKind entity, long recordId) {
        String sql = "DELETE FROM crm.field_value WHERE definition_id = :definitionId AND "
                + recordColumn(entity) + " = :recordId";
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("definitionId", definitionId)
                .addValue("recordId", recordId)) == 1;
    }

    private long insert(String sql, MapSqlParameterSource params, String keyColumn) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(sql, params, keys, new String[]{keyColumn});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return " + keyColumn);
        }
        return key.longValue();
    }

    private static MapSqlParameterSource definitionParams(FieldRows.Definition row) {
        return new MapSqlParameterSource()
                .addValue("tenant", row.tenant())
                .addValue("entity", row.entity().stored())
                .addValue("key", row.key())
                .addValue("label", row.label())
                .addValue("type", row.type().stored())
                .addValue("required", row.required())
                .addValue("showOnRecord", row.showOnRecord())
                .addValue("showOnList", row.showOnList())
                .addValue("position", row.position())
                .addValue("agentFilled", row.agentFilled())
                .addValue("agentBrief", row.agentBrief())
                .addValue("actor", row.createdBy());
    }

    private static String recordColumn(FieldRows.Value row) {
        return recordColumn(recordKind(row));
    }

    private static FieldEntityKind recordKind(FieldRows.Value row) {
        if (row.organizationId() != null) {
            return FieldEntityKind.ORGANIZATION;
        }
        if (row.contactId() != null) {
            return FieldEntityKind.CONTACT;
        }
        if (row.dealId() != null) {
            return FieldEntityKind.DEAL;
        }
        throw new IllegalArgumentException("Field value is missing its record");
    }

    private static long recordId(FieldRows.Value row) {
        Long id = switch (recordKind(row)) {
            case ORGANIZATION -> row.organizationId();
            case CONTACT -> row.contactId();
            case DEAL -> row.dealId();
        };
        return id;
    }

    private static String recordColumn(FieldEntityKind entity) {
        return switch (entity) {
            case ORGANIZATION -> "organization_id";
            case CONTACT -> "contact_id";
            case DEAL -> "deal_id";
        };
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return switch (value) {
            case null -> null;
            case Instant instant -> instant;
            case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
            case Timestamp timestamp -> timestamp.toInstant();
            default -> throw new IllegalStateException("Unsupported timestamp type " + value.getClass().getName());
        };
    }
}
