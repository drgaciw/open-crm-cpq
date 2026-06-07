package com.opencrm.ai.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CpqAgentToolsTest {

    @Mock
    private CpqAgentToolClients clients;

    private CpqAgentTools cpqAgentTools;

    @BeforeEach
    void setUp() {
        cpqAgentTools = new CpqAgentTools(clients);
    }

    @Test
    void executePricingToolDelegatesToClient() {
        CpqToolResponse expected = CpqToolResponse.ok(null, "ok");
        when(clients.executePricing(Map.of("quoteExternalId", "Q-99"))).thenReturn(expected);

        Function<CpqAgentTools.PricingExecuteRequest, CpqToolResponse> tool =
                cpqAgentTools.executePricingTool();
        CpqToolResponse response = tool.apply(
                new CpqAgentTools.PricingExecuteRequest(Map.of("quoteExternalId", "Q-99")));

        assertSame(expected, response);
        verify(clients).executePricing(Map.of("quoteExternalId", "Q-99"));
    }

    @Test
    void getQuoteLineItemsToolDelegatesToClient() {
        CpqToolResponse expected = CpqToolResponse.gatewayUnavailable("connection refused");
        when(clients.getQuoteLineItems(7L)).thenReturn(expected);

        Function<CpqAgentTools.QuoteLineItemsRequest, CpqToolResponse> tool =
                cpqAgentTools.getQuoteLineItemsTool();
        CpqToolResponse response = tool.apply(new CpqAgentTools.QuoteLineItemsRequest(7L));

        assertSame(expected, response);
        verify(clients).getQuoteLineItems(7L);
    }

    @Test
    void validateConfigurationToolDelegatesToClient() {
        CpqToolResponse expected = CpqToolResponse.clientError("400 Bad Request");
        when(clients.validateConfiguration(List.of(10L, 20L))).thenReturn(expected);

        Function<CpqAgentTools.ConfigurationValidateRequest, CpqToolResponse> tool =
                cpqAgentTools.validateConfigurationTool();
        CpqToolResponse response = tool.apply(
                new CpqAgentTools.ConfigurationValidateRequest(List.of(10L, 20L)));

        assertSame(expected, response);
        verify(clients).validateConfiguration(List.of(10L, 20L));
    }
}
