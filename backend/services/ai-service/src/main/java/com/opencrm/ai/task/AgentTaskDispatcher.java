package com.opencrm.ai.task;

import org.springframework.context.SmartLifecycle;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Leases due tasks and calls the handler for their kind. It does not decide what the work means.
 */
public class AgentTaskDispatcher implements SmartLifecycle {

    private final AgentTaskService tasks;
    private final AgentTaskProperties properties;
    private final List<AgentTaskHandler> handlers;
    private final String generatedOwner = "ai-dispatcher-" + UUID.randomUUID();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ai-agent-task-dispatcher");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running;

    public AgentTaskDispatcher(AgentTaskService tasks, AgentTaskProperties properties, List<AgentTaskHandler> handlers) {
        this.tasks = tasks;
        this.properties = properties;
        this.handlers = handlers == null ? List.of() : List.copyOf(handlers);
    }

    public void poll() {
        List<AgentTask> claimed = tasks.claim(leaseOwner(), properties.getDispatcher().getClaimLimit());
        if (claimed == null) {
            return;
        }
        for (AgentTask task : claimed) {
            dispatch(task);
        }
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        long delay = Math.max(1L, properties.getDispatcher().getPollInterval().toMillis());
        executor.scheduleWithFixedDelay(this::pollSafely, delay, delay, TimeUnit.MILLISECONDS);
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        executor.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void pollSafely() {
        try {
            poll();
        } catch (RuntimeException ex) {
            // The loop stays up. A failed tick is retried on the next delay.
        }
    }

    private void dispatch(AgentTask task) {
        try {
            AgentTaskHandler handler = handlerFor(task.getKind());
            if (handler == null) {
                tasks.fail(task.getId(), leaseOwner(), "unknown kind: " + task.getKind());
                return;
            }
            handler.handle(task);
            tasks.complete(task.getId(), leaseOwner());
        } catch (RuntimeException ex) {
            try {
                tasks.fail(task.getId(), leaseOwner(), ex.getMessage());
            } catch (RuntimeException ignored) {
                // A lost lease must not leave the loop.
            }
        }
    }

    private AgentTaskHandler handlerFor(String kind) {
        for (AgentTaskHandler handler : handlers) {
            if (kind != null && kind.equals(handler.kind())) {
                return handler;
            }
        }
        return null;
    }

    private String leaseOwner() {
        String configured = properties.getDispatcher().getLeaseOwner();
        if (configured == null || configured.isBlank()) {
            return generatedOwner;
        }
        return configured;
    }
}
