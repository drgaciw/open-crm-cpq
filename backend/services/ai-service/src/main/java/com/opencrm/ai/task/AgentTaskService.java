package com.opencrm.ai.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentTaskService {

    private final AgentTaskRepository repository;
    private final AgentTaskProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    @Autowired
    public AgentTaskService(AgentTaskRepository repository, AgentTaskProperties properties,
                            ObjectProvider<Clock> clock, ObjectMapper objectMapper,
                            PlatformTransactionManager transactionManager) {
        this(repository, properties, clock.getIfAvailable(Clock::systemUTC), objectMapper, transactionManager);
    }

    public AgentTaskService(AgentTaskRepository repository, AgentTaskProperties properties, Clock clock,
                            ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public AgentTask enqueue(AgentTaskEnqueue request) {
        validate(request);
        try {
            return transactions.execute(status -> insertOrReturn(request));
        } catch (DataIntegrityViolationException ex) {
            AgentTask winner = transactions.execute(status -> findUnfinished(request).orElse(null));
            if (winner == null) {
                throw ex;
            }
            return winner;
        }
    }

    public List<AgentTask> claim(String leaseOwner, int limit) {
        if (leaseOwner == null || leaseOwner.isBlank()) {
            throw new IllegalArgumentException("leaseOwner is required");
        }
        int bounded = AgentTaskLimits.clampClaimLimit(limit);
        return transactions.execute(status -> {
            Instant now = now();
            List<AgentTask> due = repository.lockDue(now, bounded);
            Instant until = now.plus(AgentTaskLimits.clampLease(properties.getDispatcher().getLease()));
            for (AgentTask task : due) {
                task.setLeaseOwner(leaseOwner);
                task.setLeasedUntil(until);
                if (task.getStartedAt() == null) {
                    task.setStartedAt(now);
                }
            }
            return due;
        });
    }

    public AgentTask complete(UUID id, String leaseOwner) {
        return transactions.execute(status -> {
            AgentTask task = locked(id);
            requireLiveLease(task, leaseOwner);
            task.setOutcome(Outcome.SUCCEEDED);
            task.setFinishedAt(now());
            task.setLeaseOwner(null);
            task.setLeasedUntil(null);
            return task;
        });
    }

    public AgentTask fail(UUID id, String leaseOwner, String error) {
        return transactions.execute(status -> {
            AgentTask task = locked(id);
            requireLiveLease(task, leaseOwner);
            int attempts = task.getAttempts() + 1;
            task.setAttempts(attempts);
            task.setLastError(AgentTaskLimits.truncateError(error));
            task.setLeaseOwner(null);
            task.setLeasedUntil(null);
            if (attempts >= task.getMaxAttempts()) {
                task.setOutcome(Outcome.PARKED);
                task.setFinishedAt(now());
                return task;
            }
            Duration delay = AgentTaskLimits.backoff(
                    attempts,
                    properties.getRetry().getInitialBackoff(),
                    properties.getRetry().getMultiplier(),
                    properties.getRetry().getMaxBackoff());
            task.setDueAt(now().plus(delay));
            return task;
        });
    }

    public AgentTask extendLease(UUID id, String leaseOwner, Duration extension) {
        return transactions.execute(status -> {
            AgentTask task = locked(id);
            requireLiveLease(task, leaseOwner);
            task.setLeasedUntil(task.getLeasedUntil().plus(AgentTaskLimits.clampLease(extension)));
            return task;
        });
    }

    private AgentTask insertOrReturn(AgentTaskEnqueue request) {
        Optional<AgentTask> existing = findUnfinished(request);
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant now = now();
        AgentTask task = new AgentTask();
        task.setId(UUID.randomUUID());
        task.setTenantId(request.tenantId());
        task.setKind(request.kind());
        task.setSubjectType(request.subjectType());
        task.setSubjectId(request.subjectId());
        task.setReason(request.reason());
        task.setPayload(normalizePayload(request.payload()));
        task.setPriority(request.priority() == null ? properties.getDefaultPriority() : request.priority());
        task.setBudget(request.budget());
        task.setAttempts(0);
        task.setMaxAttempts(maxAttempts(request));
        task.setDueAt(request.dueAt() == null ? now : request.dueAt());
        task.setCreatedAt(now);
        return repository.saveAndFlush(task);
    }

    private Optional<AgentTask> findUnfinished(AgentTaskEnqueue request) {
        return repository.findUnfinished(request.tenantId(), request.kind(), request.subjectType(), request.subjectId());
    }

    private AgentTask locked(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        return repository.lockById(id).orElseThrow(() -> new IllegalArgumentException("task not found"));
    }

    private void requireLiveLease(AgentTask task, String leaseOwner) {
        if (task.getFinishedAt() != null) {
            throw new IllegalStateException("task is finished");
        }
        if (leaseOwner == null || !leaseOwner.equals(task.getLeaseOwner())) {
            throw new IllegalStateException("lease owner mismatch");
        }
        Instant until = task.getLeasedUntil();
        if (until == null || until.isBefore(now())) {
            throw new IllegalStateException("lease is not live");
        }
    }

    private void validate(AgentTaskEnqueue request) {
        if (request == null || request.tenantId() == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (request.kind() == null || request.kind().isBlank()) {
            throw new IllegalArgumentException("kind is required");
        }
        if (request.subjectType() == null || request.subjectType().isBlank()) {
            throw new IllegalArgumentException("subjectType is required");
        }
        if (request.subjectId() == null || request.subjectId().isBlank()) {
            throw new IllegalArgumentException("subjectId is required");
        }
        if (request.budget() != null && request.budget() < 0) {
            throw new IllegalArgumentException("budget must not be negative");
        }
        maxAttempts(request);
        normalizePayload(request.payload());
    }

    private int maxAttempts(AgentTaskEnqueue request) {
        int attempts = request.maxAttempts() == null ? properties.getDefaultMaxAttempts() : request.maxAttempts();
        if (attempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        return attempts;
    }

    private String normalizePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            objectMapper.readTree(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("payload must be JSON");
        }
        return payload;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
