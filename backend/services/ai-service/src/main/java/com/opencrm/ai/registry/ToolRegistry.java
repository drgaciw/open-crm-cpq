package com.opencrm.ai.registry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import java.util.function.Function;

@Configuration
public class ToolRegistry {

    @Bean
    @Description("Query the CRM Customer database by Segment")
    public Function<CustomerSegmentRequest, CustomerSegmentResponse> customerSegmentTool() {
        return request -> new CustomerSegmentResponse("Simulated Response: 45 enterprise customers");
    }

    @Bean
    @Description("Check pricing margin threshold guardrails")
    public Function<MarginCheckRequest, MarginCheckResponse> marginCheckTool() {
        return request -> new MarginCheckResponse("Simulated Response: Margin is acceptable (35%)");
    }

    public record CustomerSegmentRequest(String segment) {}
    public record CustomerSegmentResponse(String result) {}
    
    public record MarginCheckRequest(double cost, double price) {}
    public record MarginCheckResponse(String result) {}
}
