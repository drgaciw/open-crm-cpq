package com.opencrm.quote.client;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("ApprovalRoutingClient")
class ApprovalRoutingClientTest {

    private MockRestServiceServer server;
    private ApprovalRoutingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://routing.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ApprovalRoutingClient(builder.build());
    }

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    @Test
    @DisplayName("submit POSTs to /api/v1/approvals and parses PENDING")
    void submitParsesCreated() {
        server.expect(requestTo("http://routing.test/api/v1/approvals"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"quoteId":"42","tier":"DEAL_DESK","requesterId":"agent","reason":"Quote approval submitted via BFF"}
                        """))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "message": "Approval request created",
                          "data": {
                            "id": 9001,
                            "uuid": "u-1",
                            "quoteId": "42",
                            "status": "PENDING",
                            "approvalTier": "DEAL_DESK",
                            "requesterId": "agent"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        RoutingApprovalDto dto = client.submit(RoutingApprovalSubmitRequest.builder()
                .quoteId("42")
                .tier("DEAL_DESK")
                .requesterId("agent")
                .reason("Quote approval submitted via BFF")
                .build());

        assertThat(dto.getId()).isEqualTo(9001L);
        assertThat(dto.getQuoteId()).isEqualTo("42");
        assertThat(dto.getStatus()).isEqualTo("PENDING");
        assertThat(dto.getApprovalTier()).isEqualTo("DEAL_DESK");
    }

    @Test
    @DisplayName("getPending parses list payload")
    void getPendingParsesList() {
        server.expect(requestTo("http://routing.test/api/v1/approvals/pending"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [
                            {"id": 1, "quoteId": "7", "status": "PENDING", "approvalTier": "CFO"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RoutingApprovalDto> pending = client.getPending();
        assertThat(pending).hasSize(1);
        assertThat(pending.getFirst().getApprovalTier()).isEqualTo("CFO");
    }

    @Test
    @DisplayName("getById maps 404 to ResourceNotFoundException")
    void getByIdNotFound() {
        server.expect(requestTo("http://routing.test/api/v1/approvals/55"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.getById(55L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("approve maps transport/API failure to BusinessException")
    void approveMapsEmptyBody() {
        server.expect(requestTo("http://routing.test/api/v1/approvals/9/approve?approverId=mgr"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"success": false, "message": "boom"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.approve(9L, "mgr"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("APPROVAL_ROUTING_APPROVE_FAILED");
    }
}
