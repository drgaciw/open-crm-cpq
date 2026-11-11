package com.opencrm.ai.ledger;

import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.opencrm.ai.ledger.LedgerTypes.*;

@Service
@Transactional
public class AgentLedgerService {
    private final AgentLedgerRepository repository;
    private final Clock clock;
    private final Validator validator;
    @Autowired
    public AgentLedgerService(AgentLedgerRepository repository, ObjectProvider<Clock> clock, Validator validator) {
        this(repository, clock.getIfAvailable(Clock::systemUTC), validator);
    }
    public AgentLedgerService(AgentLedgerRepository repository, Clock clock, Validator validator) {
        this.repository = repository; this.clock = clock; this.validator = validator;
    }
    public Run start(String tenant, Start input, String actor) {
        tenant(tenant); validate(input);
        if (!repository.taskExists(tenant, input.taskId())) throw missing();
        Run run = new Run(UUID.randomUUID(), tenant, input.taskId(), Status.RUNNING, input.provider(), input.model(),
                0,0,0, BigDecimal.ZERO.setScale(12), now(), null,null,null);
        repository.insert(run);
        append(tenant, run.id(), "RUN_STARTED", actor);
        return run;
    }
    public Action recordAction(String tenant, UUID id, ActionInput input, String actor) {
        validate(input);
        Run run = locked(tenant,id); running(run);
        // Detect token overflow before the insert; database arithmetic is also checked.
        Math.addExact(run.totalTokens(), Math.addExact(input.promptTokens(),input.completionTokens()));
        Action action = new Action(UUID.randomUUID(),id,input,input.cost(),now());
        repository.insertAction(tenant,action);
        append(tenant,id,"ACTION_RECORDED",actor);
        return action;
    }
    public void complete(String tenant, UUID id, String actor) { finish(tenant,id,Status.SUCCEEDED,null,actor); }
    public void fail(String tenant, UUID id, String errorCode, String actor) {
        validate(new Failure(errorCode)); finish(tenant,id,Status.FAILED,errorCode,actor);
    }
    private void finish(String tenant, UUID id, Status status, String error, String actor) {
        Run run = locked(tenant,id); running(run);
        Instant end = now().isBefore(run.startedAt()) ? run.startedAt() : now();
        repository.finish(tenant,id,status,end,Duration.between(run.startedAt(),end).toMillis(),error);
        append(tenant,id,"RUN_" + status.name(),actor);
    }
    public Audit audit(String tenant, UUID id, String eventType, String actor) {
        validate(new AuditInput(eventType)); locked(tenant,id);
        return append(tenant,id,eventType,actor);
    }
    @Transactional(readOnly=true)
    public Run get(String tenant, UUID id) { tenant(tenant); return repository.find(tenant,id,false).orElseThrow(AgentLedgerService::missing); }
    @Transactional(readOnly=true)
    public List<Action> actions(String tenant, UUID id) { get(tenant,id); return repository.actions(tenant,id); }
    @Transactional(readOnly=true)
    public List<Audit> audits(String tenant, UUID id) { get(tenant,id); return repository.audits(tenant,id); }
    @Transactional(readOnly=true)
    public Usage usage(String tenant, UUID taskId) { tenant(tenant); return repository.usage(tenant,taskId); }
    private Run locked(String tenant, UUID id) { tenant(tenant); return repository.find(tenant,id,true).orElseThrow(AgentLedgerService::missing); }
    private Audit append(String tenant, UUID id, String type, String actor) {
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("actor is required");
        Audit audit = new Audit(UUID.randomUUID(),id,type,actor,now()); repository.append(tenant,audit); return audit;
    }
    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) throw new IllegalArgumentException("invalid ledger request");
    }
    private static void tenant(String tenant) { if (tenant == null) throw new IllegalArgumentException("tenant is required"); }
    private static void running(Run run) {
        if (run.status()!=Status.RUNNING) throw new ResponseStatusException(HttpStatus.CONFLICT,"run is terminal");
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"run or task not found"); }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
}
