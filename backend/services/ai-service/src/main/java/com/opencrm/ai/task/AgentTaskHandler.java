package com.opencrm.ai.task;

/**
 * Work for one {@code kind}. The dispatcher only resolves and calls this.
 * No production handler ships with the queue; enforcement of {@code budget} belongs to the handler.
 */
public interface AgentTaskHandler {

    String kind();

    void handle(AgentTask task);
}
