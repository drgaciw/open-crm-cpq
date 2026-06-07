package com.opencrm.ai.registry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * CPQ agent tools wired to agent-contract gateway paths (Lane 2b).
 *
 * <p>Simulated customer-segment and margin tools remain in {@link ToolRegistry} until
 * real endpoints exist. Spring AI 0.8.1 registers tools as {@link Function} beans with
 * {@link Description}; {@code @Tool} method annotations require Spring AI 1.0+.</p>
 */
@Configuration
public class CpqAgentTools {

    private final CpqAgentToolClients clients;

    public CpqAgentTools(CpqAgentToolClients clients) {
        this.clients = clients;
    }

    @Bean
    @Description("""
            Execute deterministic pricing with waterfall and audit via CPQ gateway \
            (agent-contract POST /api/v1/pricing/execute). Used by cpq-agent-011.""")
    public Function<PricingExecuteRequest, CpqToolResponse> executePricingTool() {
        return request -> clients.executePricing(request.pricingContext());
    }

    @Bean
    @Description("""
            Fetch quote line items for margin and compatibility analysis via CPQ gateway \
            (agent-contract GET /api/v1/quotes/{quoteId}/line-items). Used by cpq-agent-011 and cpq-agent-015.""")
    public Function<QuoteLineItemsRequest, CpqToolResponse> getQuoteLineItemsTool() {
        return request -> clients.getQuoteLineItems(request.quoteId());
    }

    @Bean
    @Description("""
            Validate product configuration compatibility via CPQ gateway \
            (agent-contract POST /api/v1/configuration/validate). Used by cpq-agent-015.""")
    public Function<ConfigurationValidateRequest, CpqToolResponse> validateConfigurationTool() {
        return request -> clients.validateConfiguration(request.selectedProductIds());
    }

    public record PricingExecuteRequest(Map<String, Object> pricingContext) {}

    public record QuoteLineItemsRequest(long quoteId) {}

    public record ConfigurationValidateRequest(List<Long> selectedProductIds) {}
}
