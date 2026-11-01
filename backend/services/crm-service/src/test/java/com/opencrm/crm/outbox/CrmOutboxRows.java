package com.opencrm.crm.outbox;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/** Reads {@code outbox_events} rows written in the same CRM schema as the domain change. */
final class CrmOutboxRows {

    private final JdbcTemplate jdbc;

    CrmOutboxRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Map<String, Object>> forAggregate(String aggregateId, String topic) {
        return jdbc.queryForList(
                "SELECT topic, event_type, actor, payload, status, published, aggregate_type, aggregate_id "
                        + "FROM outbox_events WHERE aggregate_id = ? AND (topic = ? OR event_type = ?)",
                aggregateId, topic, topic);
    }

    long organizations(String uuid) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE uuid = ?", Long.class, uuid);
        return count == null ? 0 : count;
    }

    String payload(Map<String, Object> row) {
        Object payload = row.get("payload");
        return payload == null ? "" : payload.toString();
    }

    String actor(Map<String, Object> row) {
        Object actor = row.get("actor");
        return actor == null ? null : actor.toString();
    }
}
