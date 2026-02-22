package com.opencrm.ai.agent;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

public interface CpqAgent {
    
    /**
     * Unique identifier for this agent, e.g. "cpq-agent-001"
     */
    String getAgentId();
    
    /**
     * The human readable name of this agent.
     */
    String getName();
    
    /**
     * The purpose or functional description of this agent.
     */
    String getDescription();
    
    /**
     * Executes the agent task synchronously.
     */
    AgentResponse invoke(AgentRequest request);
    
    /**
     * Executes the agent task asynchronously, streaming back chunks.
     */
    Flux<String> stream(AgentRequest request);
}
