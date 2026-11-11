package com.opencrm.ai.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.opencrm.ai.ledger.LedgerTypes.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentLedgerRepositoryTest {
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    AgentLedgerRepository repo=new AgentLedgerRepository(jdbc);
    UUID id=UUID.randomUUID();
    @Test void runLookupAlwaysScopesTenantAndLocksForWrites() {
        repo.find("tenant",id,true);
        verify(jdbc).query(contains("WHERE tenant_id=? AND id=? FOR UPDATE"),any(RowMapper.class),eq("tenant"),eq(id));
    }
    @Test void auditPersistenceIsInsertOnlyAndScoped() {
        Audit audit=new Audit(UUID.randomUUID(),id,"REVIEWED","actor",Instant.now()); repo.append("tenant",audit);
        verify(jdbc).update(startsWith("INSERT INTO ai.agent_audit_event"),eq(audit.id()),eq("tenant"),eq(id),
                eq("REVIEWED"),eq("actor"),any());
    }
    @Test void meteringUpdatesRollupWithExactDecimals() {
        var input=new ActionInput("LLM","chat","azure","gpt",3,7,new BigDecimal("0.125"),new BigDecimal("0.375"),4);
        Action action=new Action(UUID.randomUUID(),id,input,input.cost(),Instant.now()); repo.insertAction("tenant",action);
        verify(jdbc).update(contains("cost_usd=cost_usd+? WHERE tenant_id=? AND id=? AND status='RUNNING'"),
                eq(3L),eq(7L),eq(10L),eq(new BigDecimal("0.000003000000")),eq("tenant"),eq(id));
    }
    @Test void aggregateQueriesAreBoundToTenantAndOptionalTask() {
        Usage total=new Usage(2,3,7,new BigDecimal("0.000003"));
        when(jdbc.query(anyString(),any(RowMapper.class),eq("tenant"))).thenReturn(List.of(total));
        when(jdbc.query(anyString(),any(RowMapper.class),eq("tenant"),eq(id))).thenReturn(List.of(total));
        assertThat(repo.usage("tenant",null)).isEqualTo(total); assertThat(repo.usage("tenant",id)).isEqualTo(total);
        verify(jdbc).query(contains("WHERE tenant_id=? AND task_id=?"),any(RowMapper.class),eq("tenant"),eq(id));
    }
    @Test void migrationEnforcesTenantCorrelationAndAppendOnlyHistory() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V4__agent_run_ledger.sql"));
        assertThat(sql).contains("FOREIGN KEY (tenant_id, task_id) REFERENCES ai.agent_task (tenant_id, id)",
                "FOREIGN KEY (tenant_id, run_id) REFERENCES ai.agent_run (tenant_id, id)",
                "agent_action_append_only BEFORE UPDATE OR DELETE", "agent_audit_event_append_only BEFORE UPDATE OR DELETE",
                "agent_action_no_truncate BEFORE TRUNCATE", "agent_audit_event_no_truncate BEFORE TRUNCATE",
                "OLD.status <> 'RUNNING'", "NUMERIC(24,12)","total_tokens = prompt_tokens + completion_tokens");
        assertThat(sql).doesNotContain("CREATE POLICY", "CREATE TOPIC");
    }
}
