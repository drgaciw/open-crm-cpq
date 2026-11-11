package com.opencrm.ai.ledger;

import com.opencrm.ai.task.AgentTask;
import com.opencrm.ai.task.AgentTaskHandler;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import static com.opencrm.ai.ledger.LedgerTypes.*;

/** Decorates execution only; queue leasing, budget enforcement and retries remain with the queue/handler. */
public final class LedgerTaskHandler implements AgentTaskHandler {
    private static final ThreadLocal<Execution> CURRENT = new ThreadLocal<>();
    private final AgentTaskHandler delegate;
    private final AgentLedgerService ledger;
    public LedgerTaskHandler(AgentTaskHandler delegate, AgentLedgerService ledger) {
        this.delegate=delegate; this.ledger=ledger;
    }
    @Override public String kind() { return delegate.kind(); }
    /** Synchronous handlers can report actual LLM/tool usage; no fabricated usage for handler-only actions. */
    public static void meter(ActionInput usage) {
        Execution execution = CURRENT.get();
        if (execution == null) throw new IllegalStateException("no active task run");
        execution.ledger.recordAction(execution.tenant,execution.run,usage,"task-handler");
    }
    @Override public void handle(AgentTask task) {
        Run run = ledger.start(task.getTenantId(),new Start(task.getId(),"unknown","unknown"),"task-dispatcher");
        Execution previous = CURRENT.get();
        CURRENT.set(new Execution(ledger,task.getTenantId(),run.id()));
        Instant start = Instant.now();
        try {
            delegate.handle(task);
            recordHandler(task,run,start);
            ledger.complete(task.getTenantId(),run.id(),"task-dispatcher");
        } catch (RuntimeException error) {
            try {
                recordHandler(task,run,start);
            } catch (RuntimeException actionError) { error.addSuppressed(actionError); }
            try {
                ledger.fail(task.getTenantId(),run.id(),"HANDLER_FAILED","task-dispatcher");
            } catch (RuntimeException ledgerError) { error.addSuppressed(ledgerError); }
            throw error;
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
    private void recordHandler(AgentTask task, Run run, Instant start) {
        ledger.recordAction(task.getTenantId(),run.id(),new ActionInput("HANDLER",kind(),"none","none",0,0,
                BigDecimal.ZERO,BigDecimal.ZERO,Math.max(0,Duration.between(start,Instant.now()).toMillis())),"task-handler");
    }
    private record Execution(AgentLedgerService ledger, String tenant, UUID run) {}
}
