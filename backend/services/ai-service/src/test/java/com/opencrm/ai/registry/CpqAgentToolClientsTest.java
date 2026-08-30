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
    void executePricingUnwrapsApiResponseAndExposesSuccessAndLineResults() {
        server.expect(requestTo("http://gateway.test/api/v1/pricing/execute"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"quoteExternalId\":\"Q-1\"}"))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "message": "Pricing executed",
                          "data": {
                            "success": true,
                            "lineResults": [{"productExternalId":"SKU-1","netPrice":100.0}],
                            "errorMessage": null
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        CpqToolResponse response = clients.executePricing(Map.of("quoteExternalId", "Q-1"));

        assertTrue(response.success());
        assertEquals("Pricing executed", response.message());
        assertNotNull(response.data());
        assertTrue(response.data().get("success").asBoolean());
        assertTrue(response.data().get("lineResults").isArray());
        assertEquals(1, response.data().get("lineResults").size());
        assertEquals("SKU-1", response.data().get("lineResults").get(0).get("productExternalId").asText());
    }

    @Test
    void getQuoteLineItemsUnwrapsApiResponseArray() {
        server.expect(requestTo("http://gateway.test/api/v1/quotes/42/line-items"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "message": "Line items",
                          "data": [{"id":1,"quantity":2,"productExternalId":"SKU-1"}]
                        }
                        """, MediaType.APPLICATION_JSON));

        CpqToolResponse response = clients.getQuoteLineItems(42);

        assertTrue(response.success());
        assertEquals("Line items", response.message());
        assertNotNull(response.data());
        assertTrue(response.data().isArray());
        assertEquals(1, response.data().size());
        assertEquals(2, response.data().get(0).get("quantity").asInt());
        assertEquals("SKU-1", response.data().get(0).get("productExternalId").asText());
    }

    @Test
    void validateConfigurationParsesStructuredValidResult() {
        server.expect(requestTo("http://gateway.test/api/v1/configuration/validate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[1,2,3]"))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "message": "Configuration is valid",
                          "data": { "valid": true, "violations": [] }
                        }
                        """, MediaType.APPLICATION_JSON));

        CpqToolResponse response = clients.validateConfiguration(List.of(1L, 2L, 3L));

        assertTrue(response.success());
        assertEquals("Configuration is valid", response.message());
        assertNotNull(response.data());
        assertTrue(response.data().get("valid").asBoolean());
        assertTrue(response.data().get("violations").isArray());
        assertEquals(0, response.data().get("violations").size());
    }

    @Test
    void validateConfigurationParsesStructuredInvalidResultWithViolations() {
        server.expect(requestTo("http://gateway.test/api/v1/configuration/validate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[10]"))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "message": "Configuration has validation errors",
                          "data": {
                            "valid": false,
                            "violations": [
                              {
                                "ruleId": 1,
                                "ruleType": "REQUIRES",
                                "description": "Product 10 requires 20",
                                "remediation": "Add product 20"
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        CpqToolResponse response = clients.validateConfiguration(List.of(10L));

        assertTrue(response.success());
        assertEquals("Configuration has validation errors", response.message());
        assertNotNull(response.data());
        assertFalse(response.data().get("valid").asBoolean());
        assertEquals(1, response.data().get("violations").size());
        assertEquals("REQUIRES", response.data().get("violations").get(0).get("ruleType").asText());
        assertEquals("Product 10 requires 20",
                response.data().get("violations").get(0).get("description").asText());
        assertEquals("Add product 20",
                response.data().get("violations").get(0).get("remediation").asText());
    }

    @Test
    void validateConfigurationMapsGatewayEnvelopeFailure() {
        server.expect(requestTo("http://gateway.test/api/v1/configuration/validate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"success":false,"message":"Validate unavailable","data":null}
                        """, MediaType.APPLICATION_JSON));

        CpqToolResponse response = clients.validateConfiguration(List.of(1L));

        assertFalse(response.success());
        assertEquals("CPQ_CLIENT_ERROR", response.errorCode());
        assertEquals("Validate unavailable", response.message());
        assertNull(response.data());
    }

    @Test
    void validateConfigurationMapsTransportFailure() {
        server.expect(requestTo("http://gateway.test/api/v1/configuration/validate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        CpqToolResponse response = clients.validateConfiguration(List.of(1L));

        assertFalse(response.success());
        assertEquals("CPQ_CLIENT_ERROR", response.errorCode());
        assertNotNull(response.message());
    }

    @Test
    void executePricingMapsGatewayEnvelopeFailure() {
        server.expect(requestTo("http://gateway.test/api/v1/pricing/execute"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"success":false,"message":"Pricing unavailable","data":null}
                        """, MediaType.APPLICATION_JSON));

        CpqToolResponse response = clients.executePricing(Map.of("quoteExternalId", "Q-1"));

        assertFalse(response.success());
        assertEquals("CPQ_CLIENT_ERROR", response.errorCode());
        assertEquals("Pricing unavailable", response.message());
        assertNull(response.data());
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
