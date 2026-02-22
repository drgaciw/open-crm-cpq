package com.opencrm.ai.agent.product;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.CpqAgent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.Map;

@Service("cpq-agent-001")
public class GuidedProductSelectionAgent implements CpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-001";
    }

    @Override
    public String getName() {
        return "Guided Product Selection Agent";
    }

    @Override
    public String getDescription() {
        return "Step-by-step product search and recommendation during quote creation";
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        // TODO: Implement actual LLM call via ChatClient
        return new AgentResponse() {
            @Override
            public boolean isSuccess() { return true; }
            @Override
            public String getOutput() { return "Simulated execution for Guided Product Selection Agent"; }
            @Override
            public Map<String, Object> getMetadata() { return Map.of("autonomy", "Advisory"); }
        };
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        return Flux.just("Starting stream for Guided Product Selection Agent...", "Processing...", "Done");
    }
}
