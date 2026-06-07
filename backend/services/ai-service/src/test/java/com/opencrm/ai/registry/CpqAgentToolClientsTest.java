package com.opencrm.ai.registry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CpqAgentToolClientsTest {

    private MockRestServiceServer server;
    private CpqAgentToolClients clients;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.test");
        server = MockRestServiceServer.bindTo(builder).build();
        clients = new CpqAgentToolClients(builder.build());
    }

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    @Test
    void executePricingReturnsGatewayPayload() {
        server.expect(requestTo("http://gateway.test/api/v1/pricing/execute"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"quoteExternalId\":\"Q-1\"}"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"finalPrice":100.0}}
                        """, MediaType.APPLICATION_JSON));

        CpqToolResponse response = clients.executePricing(Map.of("quoteExternalId", "Q-1"));

        assertTrue(response.success());
        assertEquals("Gateway call succeeded", response.message());
        assertNotNull(response.data());
        assertTrue(response.data().get("success").asBoolean());
    }

    @Test
    void getQuoteLineItemsReturnsGatewayPayload() {
        server.expect(requestTo("http://gateway.test/api/v1/quotes/42/line-items"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"success":true,"data":[{"id":1,"quantity":2}]}
                        """, MediaType.APPLICATION_JSON));

        CpqToolResponse response = clients.getQuoteLineItems(42);

        assertTrue(response.success());
        assertNotNull(response.data());
        assertTrue(response.data().get("data").isArray());
    }

    @Test
    void validateConfigurationReturnsGatewayPayload() {
        server.expect(requestTo("http://gateway.test/api/v1/configuration/validate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[1,2,3]"))
                .andRespond(withSuccess("""
                        {"success":true,"data":[],"message":"Configuration is valid"}
                        """, MediaType.APPLICATION_JSON));

        CpqToolResponse response = clients.validateConfiguration(List.of(1L, 2L, 3L));

        assertTrue(response.success());
        assertNotNull(response.data());
        assertTrue(response.data().get("data").isArray());
    }

    @Test
    void executePricingReturnsStructuredErrorWhenGatewayUnreachable() {
        server.expect(requestTo("http://gateway.test/api/v1/pricing/execute"))
                .andRespond(withServerError());

        CpqToolResponse response = clients.executePricing(Map.of("quoteExternalId", "Q-1"));

        assertFalse(response.success());
        assertEquals("CPQ_CLIENT_ERROR", response.errorCode());
        assertNotNull(response.message());
    }
}
