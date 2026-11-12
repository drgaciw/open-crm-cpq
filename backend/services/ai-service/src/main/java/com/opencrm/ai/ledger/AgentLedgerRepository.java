package com.opencrm.ai.ledger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static com.opencrm.ai.ledger.LedgerTypes.*;

@Repository
public class AgentLedgerRepository {
    private final JdbcTemplate jdbc;
    public AgentLedgerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final RowMapper<Run> RUN = (rs, n) -> new Run(rs.getObject("id", UUID.class),
            rs.getString("tenant_id"), rs.getObject("task_id", UUID.class), Status.valueOf(rs.getString("status")),
            rs.getString("provider"), rs.getString("model"), rs.getLong("prompt_tokens"),
            rs.getLong("completion_tokens"), rs.getLong("total_tokens"), rs.getBigDecimal("cost_usd"),
            rs.getTimestamp("started_at").toInstant(), instant(rs.getTimestamp("finished_at")),
            rs.getObject("latency_ms", Long.class), rs.getString("error_code"));
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

    public boolean taskExists(String tenant, UUID task) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ai.agent_task WHERE tenant_id=? AND id=?)",
                Boolean.class, tenant, task));
    }
    public void insert(Run run) {
        jdbc.update("INSERT INTO ai.agent_run(id,tenant_id,task_id,provider,model,started_at) VALUES (?,?,?,?,?,?)",
                run.id(), run.tenantId(), run.taskId(), run.provider(), run.model(), Timestamp.from(run.startedAt()));
    }
    public Optional<Run> find(String tenant, UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM ai.agent_run WHERE tenant_id=? AND id=?" + (lock ? " FOR UPDATE" : ""),
                RUN, tenant, id).stream().findFirst();
    }
    public void insertAction(String tenant, Action action) {
        ActionInput u = action.usage();
        jdbc.update("""
                INSERT INTO ai.agent_action(id,tenant_id,run_id,kind,tool,provider,model,prompt_tokens,
                  completion_tokens,total_tokens,input_usd_per_million,output_usd_per_million,cost_usd,latency_ms,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, action.id(), tenant, action.runId(), u.kind(), u.tool(), u.provider(), u.model(),
                u.promptTokens(), u.completionTokens(), Math.addExact(u.promptTokens(), u.completionTokens()),
                u.inputUsdPerMillion(), u.outputUsdPerMillion(), action.costUsd(), u.latencyMs(), Timestamp.from(action.createdAt()));
        jdbc.update("""
                UPDATE ai.agent_run SET prompt_tokens=prompt_tokens+?, completion_tokens=completion_tokens+?,
                  total_tokens=total_tokens+?, cost_usd=cost_usd+? WHERE tenant_id=? AND id=? AND status='RUNNING'
                """, u.promptTokens(), u.completionTokens(), Math.addExact(u.promptTokens(), u.completionTokens()),
                action.costUsd(), tenant, action.runId());
    }
    public void append(String tenant, Audit event) {
        jdbc.update("INSERT INTO ai.agent_audit_event(id,tenant_id,run_id,event_type,actor,created_at) VALUES (?,?,?,?,?,?)",
                event.id(), tenant, event.runId(), event.eventType(), event.actor(), Timestamp.from(event.createdAt()));
    }
    public void finish(String tenant, UUID id, Status status, Instant end, long latency, String error) {
        jdbc.update("""
                UPDATE ai.agent_run SET status=?,finished_at=?,latency_ms=?,error_code=?
                WHERE tenant_id=? AND id=? AND status='RUNNING'
                """, status.name(), Timestamp.from(end), latency, error, tenant, id);
    }
    public List<Action> actions(String tenant, UUID id) {
        return jdbc.query("SELECT * FROM ai.agent_action WHERE tenant_id=? AND run_id=? ORDER BY created_at,id",
                (rs,n) -> new Action(rs.getObject("id",UUID.class), id,
                    new ActionInput(rs.getString("kind"),rs.getString("tool"),rs.getString("provider"),rs.getString("model"),
                        rs.getLong("prompt_tokens"),rs.getLong("completion_tokens"),rs.getBigDecimal("input_usd_per_million"),
                        rs.getBigDecimal("output_usd_per_million"),rs.getLong("latency_ms")),
                    rs.getBigDecimal("cost_usd"),rs.getTimestamp("created_at").toInstant()), tenant,id);
    }
    public List<Audit> audits(String tenant, UUID id) {
        return jdbc.query("SELECT * FROM ai.agent_audit_event WHERE tenant_id=? AND run_id=? ORDER BY created_at,id",
                (rs,n) -> new Audit(rs.getObject("id",UUID.class),id,rs.getString("event_type"),rs.getString("actor"),
                        rs.getTimestamp("created_at").toInstant()),tenant,id);
    }
    public Usage usage(String tenant, UUID task) {
        String sql = "SELECT count(*) runs, coalesce(sum(prompt_tokens),0) prompt, coalesce(sum(completion_tokens),0) completion, "
                + "coalesce(sum(cost_usd),0) cost FROM ai.agent_run WHERE tenant_id=?" + (task == null ? "" : " AND task_id=?");
        RowMapper<Usage> mapper = (rs,n) -> new Usage(rs.getLong("runs"), rs.getLong("prompt"),
                rs.getLong("completion"),rs.getBigDecimal("cost"));
        return (task == null ? jdbc.query(sql,mapper,tenant) : jdbc.query(sql,mapper,tenant,task)).getFirst();
    }
}
