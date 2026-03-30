package com.opencrm.ai.registry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Function;

class ToolRegistryTest {

    private final ToolRegistry toolRegistry = new ToolRegistry();

    @Test
    void testCustomerSegmentTool() {
        Function<ToolRegistry.CustomerSegmentRequest, ToolRegistry.CustomerSegmentResponse> tool = toolRegistry.customerSegmentTool();

        ToolRegistry.CustomerSegmentRequest request = new ToolRegistry.CustomerSegmentRequest("enterprise");
        ToolRegistry.CustomerSegmentResponse response = tool.apply(request);

        assertNotNull(response, "Response should not be null");
        assertEquals("Simulated Response: 45 enterprise customers", response.result(), "Response result should match simulated output");
    }

    @Test
    void testMarginCheckTool() {
        Function<ToolRegistry.MarginCheckRequest, ToolRegistry.MarginCheckResponse> tool = toolRegistry.marginCheckTool();

        ToolRegistry.MarginCheckRequest request = new ToolRegistry.MarginCheckRequest(100.0, 150.0);
        ToolRegistry.MarginCheckResponse response = tool.apply(request);

        assertNotNull(response, "Response should not be null");
        assertEquals("Simulated Response: Margin is acceptable (35%)", response.result(), "Response result should match simulated output");
    }
}
