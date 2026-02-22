package com.opencrm.ai.agent.subscription;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.CpqAgent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.Map;

@Service("cpq-agent-022")
public class PerpetualLicenseAgent implements CpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-022";
    }

    @Override
    public String getName() {
        return "Perpetual License Agent";
    }

    @Override
    public String getDescription() {
        return "On-prem one-time license pricing and core pack rounding";
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        // TODO: Implement actual LLM call via ChatClient
        return new AgentResponse() {
            @Override
            public boolean isSuccess() { return true; }
            @Override
            public String getOutput() { return "Simulated execution for Perpetual License Agent"; }
            @Override
            public Map<String, Object> getMetadata() { return Map.of("autonomy", "Automated"); }
        };
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        return Flux.just("Starting stream for Perpetual License Agent...", "Processing...", "Done");
    }
}
