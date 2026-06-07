package com.opencrm.ai.orchestration;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import reactor.core.publisher.Flux;

public interface WorkflowOrchestrator {

    AgentResponse invoke(String workflowId, AgentRequest request);

    Flux<String> stream(String workflowId, AgentRequest request);
}
