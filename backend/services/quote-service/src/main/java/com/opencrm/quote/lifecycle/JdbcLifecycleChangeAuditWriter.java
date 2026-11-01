package com.opencrm.quote.lifecycle;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;

/**
 * Inserts into {@code lifecycle_change_audit} and commits before the caller throws.
 * A rejection is a {@link RuntimeException}. Joining the caller's transaction would roll the
 * row back with that exception, so this write uses {@link Propagation#REQUIRES_NEW}.
 * The rejection transaction contains only this insert: no quote row and no status change.
 */
@Component
public class JdbcLifecycleChangeAuditWriter implements LifecycleChangeAuditWriter {

    static final String INSERT = """
            INSERT INTO lifecycle_change_audit (
                tid, lifecycle_change_id, subscription_id, decision_kind, reason_code,
                decided_by, inputs_json, outputs_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";

    private final JdbcTemplate jdbc;

    public JdbcLifecycleChangeAuditWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(LifecycleChangeAuditEntry entry) {
        int inserted = jdbc.update(INSERT, statement -> {
            statement.setString(1, entry.tid());
            // Null on a create rejection (no lifecycle row). pgjdbc needs an explicit uuid type.
            statement.setObject(2, entry.lifecycleChangeId(), Types.OTHER);
            if (entry.subscriptionId() == null) {
                statement.setNull(3, Types.VARCHAR);
            } else {
                statement.setString(3, entry.subscriptionId());
            }
            statement.setString(4, entry.decisionKind());
            statement.setString(5, entry.reasonCode());
            statement.setString(6, entry.decidedBy());
            statement.setString(7, entry.inputsJson());
            statement.setString(8, entry.outputsJson());
        });
        if (inserted != 1) {
            throw new IllegalStateException("lifecycle rejection was not recorded");
        }
    }
}
