package com.opencrm.ai.agent.subscription;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.CpqAgent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.Map;

@Service("cpq-agent-020")
public class TermCotermingAgent implements CpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-020";
    }

    @Override
    public String getName() {
        return "Term & Co-Terming Agent";
    }

    @Override
    public String getDescription() {
        return "Subscription term alignment and proration";
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        // TODO: Implement actual LLM call via ChatClient
        return new AgentResponse() {
            @Override
            public boolean isSuccess() { return true; }
            @Override
            public String getOutput() { return "Simulated execution for Term & Co-Terming Agent"; }
            @Override
            public Map<String, Object> getMetadata() { return Map.of("autonomy", "Semi-autonomous"); }
        };
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        return Flux.just("Starting stream for Term & Co-Terming Agent...", "Processing...", "Done");
    }
}
